use jni::JNIEnv;
use jni::objects::{JClass, JFloatArray};
use jni::sys::jfloat;
use jni::objects::ReleaseMode;

/// JNI 接口：ColBERT MaxSim 计算
/// query_tokens: q_len * dim flattened float array
/// doc_tokens: d_len * dim flattened float array
/// 返回 MaxSim 分数
#[no_mangle]
pub extern "system" fn Java_com_rag_rerank_ColbertNative_maxsim(
    mut env: JNIEnv, _class: JClass,
    query_tokens: JFloatArray,
    doc_tokens: JFloatArray,
    q_len: i32,
    d_len: i32,
    dim: i32,
) -> jfloat {
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

    // MaxSim: 对每个 query token，找到与 doc tokens 的最大相似度，然后求和
    let mut total_score: f32 = 0.0;

    for qi in 0..q_len {
        let q_offset = qi * dim;
        let mut max_sim: f32 = f32::NEG_INFINITY;

        for di in 0..d_len {
            let d_offset = di * dim;
            
            // 计算 cosine similarity
            let mut dot: f32 = 0.0;
            let mut norm_q: f32 = 0.0;
            let mut norm_d: f32 = 0.0;

            for k in 0..dim {
                let qv = q[q_offset + k];
                let dv = d[d_offset + k];
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

    total_score
}
