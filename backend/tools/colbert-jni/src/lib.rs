use jni::objects::ReleaseMode;
use jni::objects::{JClass, JFloatArray, JIntArray};
use jni::sys::{jfloat, jfloatArray};
use jni::JNIEnv;
use std::ptr;

fn expected_len(token_count: i32, dim: i32) -> Option<usize> {
    if token_count <= 0 || dim <= 0 {
        return None;
    }
    let token_count = usize::try_from(token_count).ok()?;
    let dim = usize::try_from(dim).ok()?;
    token_count.checked_mul(dim)
}

fn array_len(env: &mut JNIEnv, array: &JFloatArray) -> Option<usize> {
    usize::try_from(env.get_array_length(array).ok()?).ok()
}

fn int_array_len(env: &mut JNIEnv, array: &JIntArray) -> Option<usize> {
    usize::try_from(env.get_array_length(array).ok()?).ok()
}

fn empty_float_array(env: &mut JNIEnv) -> jfloatArray {
    match env.new_float_array(0) {
        Ok(array) => array.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn float_array(env: &mut JNIEnv, scores: &[f32]) -> jfloatArray {
    if scores.len() > i32::MAX as usize {
        return empty_float_array(env);
    }
    let result = match env.new_float_array(scores.len() as i32) {
        Ok(array) => array,
        Err(_) => return ptr::null_mut(),
    };
    if env.set_float_array_region(&result, 0, scores).is_err() {
        return empty_float_array(env);
    }
    result.into_raw()
}

fn maxsim_score(query_tokens: &[f32], doc_tokens: &[f32], q_len: usize, d_len: usize, dim: usize) -> f32 {
    if q_len == 0
        || d_len == 0
        || dim == 0
        || query_tokens.len() != q_len * dim
        || doc_tokens.len() != d_len * dim
    {
        return 0.0;
    }

    let mut total_score: f32 = 0.0;

    for qi in 0..q_len {
        let q_offset = qi * dim;
        let mut max_sim: f32 = f32::NEG_INFINITY;

        for di in 0..d_len {
            let d_offset = di * dim;
            let mut dot: f32 = 0.0;
            let mut norm_q: f32 = 0.0;
            let mut norm_d: f32 = 0.0;

            for k in 0..dim {
                let qv = query_tokens[q_offset + k];
                let dv = doc_tokens[d_offset + k];
                dot += qv * dv;
                norm_q += qv * qv;
                norm_d += dv * dv;
            }

            let denom = norm_q.sqrt() * norm_d.sqrt();
            let sim = if denom > 0.0 { dot / denom } else { 0.0 };

            if sim > max_sim {
                max_sim = sim;
            }
        }

        total_score += max_sim;
    }

    total_score / q_len as f32
}

fn maxsim_batch_scores(
    query_tokens: &[f32],
    doc_tokens: &[f32],
    doc_offsets: &[i32],
    doc_lens: &[i32],
    q_len: usize,
    dim: usize,
) -> Vec<f32> {
    if q_len == 0 || dim == 0 || query_tokens.len() != q_len * dim || doc_offsets.len() != doc_lens.len() {
        return Vec::new();
    }
    let mut scores = Vec::with_capacity(doc_offsets.len());
    for i in 0..doc_offsets.len() {
        let offset = match usize::try_from(doc_offsets[i]) {
            Ok(value) => value,
            Err(_) => return Vec::new(),
        };
        let d_len = match usize::try_from(doc_lens[i]) {
            Ok(value) => value,
            Err(_) => return Vec::new(),
        };
        let value_len = match d_len.checked_mul(dim) {
            Some(value) => value,
            None => return Vec::new(),
        };
        let end = match offset.checked_add(value_len) {
            Some(value) => value,
            None => return Vec::new(),
        };
        if end > doc_tokens.len() {
            return Vec::new();
        }
        scores.push(maxsim_score(query_tokens, &doc_tokens[offset..end], q_len, d_len, dim));
    }
    scores
}

/// JNI 接口：ColBERT MaxSim 计算
/// query_tokens: q_len * dim flattened float array
/// doc_tokens: d_len * dim flattened float array
/// 返回 MaxSim 分数
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_rerank_ColbertNative_maxsim(
    mut env: JNIEnv,
    _class: JClass,
    query_tokens: JFloatArray,
    doc_tokens: JFloatArray,
    q_len: i32,
    d_len: i32,
    dim: i32,
) -> jfloat {
    let q_expected = match expected_len(q_len, dim) {
        Some(len) => len,
        None => return 0.0,
    };
    let d_expected = match expected_len(d_len, dim) {
        Some(len) => len,
        None => return 0.0,
    };
    if array_len(&mut env, &query_tokens) != Some(q_expected)
        || array_len(&mut env, &doc_tokens) != Some(d_expected)
    {
        return 0.0;
    }
    let q_len = q_len as usize;
    let d_len = d_len as usize;
    let dim = dim as usize;

    let q = match unsafe { env.get_array_elements(&query_tokens, ReleaseMode::NoCopyBack) } {
        Ok(q) => q,
        Err(_) => return 0.0,
    };
    let d = match unsafe { env.get_array_elements(&doc_tokens, ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return 0.0,
    };

    maxsim_score(&q[..q_len * dim], &d[..d_len * dim], q_len, d_len, dim)
}

#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_rerank_ColbertNative_maxsimBatch(
    mut env: JNIEnv,
    _class: JClass,
    query_tokens: JFloatArray,
    doc_tokens: JFloatArray,
    doc_offsets: JIntArray,
    doc_lens: JIntArray,
    q_len: i32,
    dim: i32,
) -> jfloatArray {
    let q_expected = match expected_len(q_len, dim) {
        Some(len) => len,
        None => return empty_float_array(&mut env),
    };
    if array_len(&mut env, &query_tokens) != Some(q_expected) {
        return empty_float_array(&mut env);
    }
    let doc_count = match int_array_len(&mut env, &doc_offsets) {
        Some(len) => len,
        None => return empty_float_array(&mut env),
    };
    if int_array_len(&mut env, &doc_lens) != Some(doc_count) {
        return empty_float_array(&mut env);
    }
    let doc_values_len = match array_len(&mut env, &doc_tokens) {
        Some(len) => len,
        None => return empty_float_array(&mut env),
    };

    let q = match unsafe { env.get_array_elements(&query_tokens, ReleaseMode::NoCopyBack) } {
        Ok(q) => q,
        Err(_) => return empty_float_array(&mut env),
    };
    let d = match unsafe { env.get_array_elements(&doc_tokens, ReleaseMode::NoCopyBack) } {
        Ok(d) => d,
        Err(_) => return empty_float_array(&mut env),
    };
    let offsets = match unsafe { env.get_array_elements(&doc_offsets, ReleaseMode::NoCopyBack) } {
        Ok(offsets) => offsets,
        Err(_) => return empty_float_array(&mut env),
    };
    let lens = match unsafe { env.get_array_elements(&doc_lens, ReleaseMode::NoCopyBack) } {
        Ok(lens) => lens,
        Err(_) => return empty_float_array(&mut env),
    };

    let q_len = q_len as usize;
    let dim = dim as usize;
    let scores = maxsim_batch_scores(
        &q[..q_expected],
        &d[..doc_values_len],
        &offsets[..doc_count],
        &lens[..doc_count],
        q_len,
        dim,
    );
    float_array(&mut env, &scores)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn assert_close(actual: f32, expected: f32) {
        assert!((actual - expected).abs() < 1e-6, "{actual} != {expected}");
    }

    #[test]
    fn maxsim_score_averages_best_doc_token_matches() {
        let query = [
            1.0, 0.0,
            0.0, 1.0,
        ];
        let doc = [
            1.0, 0.0,
            1.0, 1.0,
        ];

        let score = maxsim_score(&query, &doc, 2, 2, 2);

        assert_close(score, (1.0 + std::f32::consts::FRAC_1_SQRT_2) / 2.0);
    }

    #[test]
    fn maxsim_score_returns_zero_for_invalid_shapes() {
        assert_close(maxsim_score(&[1.0], &[1.0], 0, 1, 1), 0.0);
        assert_close(maxsim_score(&[1.0], &[1.0], 1, 1, 2), 0.0);
    }

    #[test]
    fn maxsim_batch_scores_scores_multiple_documents() {
        let query = [1.0, 0.0];
        let docs = [
            1.0, 0.0,
            0.0, 1.0,
        ];
        let scores = maxsim_batch_scores(&query, &docs, &[0, 2], &[1, 1], 1, 2);

        assert_eq!(scores.len(), 2);
        assert_close(scores[0], 1.0);
        assert_close(scores[1], 0.0);
    }
}
