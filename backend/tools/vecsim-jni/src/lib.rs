use jni::objects::ReleaseMode;
use jni::objects::{JClass, JFloatArray};
use jni::sys::jfloatArray;
use jni::JNIEnv;
use std::ptr;

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

fn array_len(env: &mut JNIEnv, array: &JFloatArray) -> Option<usize> {
    usize::try_from(env.get_array_length(array).ok()?).ok()
}

fn cosine_batch_scores(query: &[f32], corpus: &[f32], dim: usize) -> Vec<f32> {
    if dim == 0 || query.len() != dim || corpus.len() % dim != 0 {
        return Vec::new();
    }
    let n = corpus.len() / dim;
    let mut scores = vec![0.0f32; n];
    let q_norm: f32 = query.iter().take(dim).map(|x| x * x).sum::<f32>().sqrt();
    if q_norm == 0.0 {
        return scores;
    }

    for i in 0..n {
        let offset = i * dim;
        let mut dot: f32 = 0.0;
        let mut norm_c: f32 = 0.0;

        for j in 0..dim {
            let qv = query[j];
            let cv = corpus[offset + j];
            dot += qv * cv;
            norm_c += cv * cv;
        }

        let c_norm = norm_c.sqrt();
        if c_norm > 0.0 {
            scores[i] = dot / (q_norm * c_norm);
        }
    }
    scores
}

fn inner_product_batch_scores(query: &[f32], corpus: &[f32], dim: usize) -> Vec<f32> {
    if dim == 0 || query.len() != dim || corpus.len() % dim != 0 {
        return Vec::new();
    }
    let n = corpus.len() / dim;
    let mut scores = vec![0.0f32; n];

    for i in 0..n {
        let offset = i * dim;
        let mut dot: f32 = 0.0;
        for j in 0..dim {
            dot += query[j] * corpus[offset + j];
        }
        scores[i] = dot;
    }
    scores
}

/// JNI 接口：批量 cosine 相似度计算
/// query: 1536-dim float array
/// corpus: N * 1536 flattened float array
/// 返回 N 个相似度分数
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_sim_VecSimNative_cosineBatch(
    mut env: JNIEnv,
    _class: JClass,
    query: JFloatArray,
    corpus: JFloatArray,
    dim: i32,
) -> jfloatArray {
    if dim <= 0 {
        return empty_float_array(&mut env);
    }
    let dim = dim as usize;
    if array_len(&mut env, &query) != Some(dim) {
        return empty_float_array(&mut env);
    }
    let corpus_len = match array_len(&mut env, &corpus) {
        Some(len) if len % dim == 0 => len,
        _ => return empty_float_array(&mut env),
    };

    let q = match unsafe { env.get_array_elements(&query, ReleaseMode::NoCopyBack) } {
        Ok(q) => q,
        Err(_) => return empty_float_array(&mut env),
    };
    let c = match unsafe { env.get_array_elements(&corpus, ReleaseMode::NoCopyBack) } {
        Ok(c) => c,
        Err(_) => return empty_float_array(&mut env),
    };

    let n = corpus_len / dim;

    let scores = cosine_batch_scores(&q[..dim], &c[..n * dim], dim);

    float_array(&mut env, &scores)
}

/// JNI 接口：批量 inner product 计算
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_sim_VecSimNative_innerProductBatch(
    mut env: JNIEnv,
    _class: JClass,
    query: JFloatArray,
    corpus: JFloatArray,
    dim: i32,
) -> jfloatArray {
    if dim <= 0 {
        return empty_float_array(&mut env);
    }
    let dim = dim as usize;
    if array_len(&mut env, &query) != Some(dim) {
        return empty_float_array(&mut env);
    }
    let corpus_len = match array_len(&mut env, &corpus) {
        Some(len) if len % dim == 0 => len,
        _ => return empty_float_array(&mut env),
    };

    let q = match unsafe { env.get_array_elements(&query, ReleaseMode::NoCopyBack) } {
        Ok(q) => q,
        Err(_) => return empty_float_array(&mut env),
    };
    let c = match unsafe { env.get_array_elements(&corpus, ReleaseMode::NoCopyBack) } {
        Ok(c) => c,
        Err(_) => return empty_float_array(&mut env),
    };

    let n = corpus_len / dim;

    let scores = inner_product_batch_scores(&q[..dim], &c[..n * dim], dim);

    float_array(&mut env, &scores)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn assert_close(actual: f32, expected: f32) {
        assert!((actual - expected).abs() < 1e-6, "{actual} != {expected}");
    }

    #[test]
    fn cosine_batch_scores_handles_match_orthogonal_and_zero_vectors() {
        let query = [1.0, 0.0];
        let corpus = [1.0, 0.0, 0.0, 1.0, 0.0, 0.0];

        let scores = cosine_batch_scores(&query, &corpus, 2);

        assert_eq!(scores.len(), 3);
        assert_close(scores[0], 1.0);
        assert_close(scores[1], 0.0);
        assert_close(scores[2], 0.0);
    }

    #[test]
    fn inner_product_batch_scores_returns_dot_products() {
        let query = [2.0, 3.0];
        let corpus = [4.0, 5.0, -1.0, 2.0];

        let scores = inner_product_batch_scores(&query, &corpus, 2);

        assert_eq!(scores, vec![23.0, 4.0]);
    }

    #[test]
    fn batch_scores_reject_invalid_dimensions() {
        assert!(cosine_batch_scores(&[1.0], &[1.0, 2.0], 2).is_empty());
        assert!(inner_product_batch_scores(&[1.0], &[1.0], 0).is_empty());
    }
}
