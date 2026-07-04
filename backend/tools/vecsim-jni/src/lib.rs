use jni::JNIEnv;
use jni::objects::{JClass, JFloatArray};
use jni::sys::jfloatArray;
use jni::objects::ReleaseMode;

/// JNI 接口：批量 cosine 相似度计算
/// query: 1536-dim float array
/// corpus: N * 1536 flattened float array
/// 返回 N 个相似度分数
#[no_mangle]
pub extern "system" fn Java_com_rag_sim_VecSimNative_cosineBatch(
    mut env: JNIEnv, _class: JClass,
    query: JFloatArray,
    corpus: JFloatArray,
    dim: i32,
) -> jfloatArray {
    let dim = dim as usize;
    
    let q = match unsafe { env.get_array_elements(&query, ReleaseMode::NoCopyBack) } {
 Ok(q) => q,
        Err(_) => return env.new_float_array(0).unwrap().into_raw(),
    };
    let c = match unsafe { env.get_array_elements(&corpus, ReleaseMode::NoCopyBack) } {
        Ok(c) => c,
        Err(_) => return env.new_float_array(0).unwrap().into_raw(),
    };

    let corpus_len = c.len();
    let n = corpus_len / dim;
    
    let mut scores = vec![0.0f32; n];
    
    // 计算 query 的范数
    let q_norm: f32 = q.iter().take(dim).map(|x| x * x).sum::<f32>().sqrt();
    if q_norm == 0.0 {
        let result = env.new_float_array(n as i32).unwrap();
        env.set_float_array_region(&result, 0, &scores).unwrap();
        return result.into_raw();
    }

    // 批量计算 cosine similarity
    for i in 0..n {
        let offset = i * dim;
        let mut dot: f32 = 0.0;
        let mut norm_c: f32 = 0.0;
        
        for j in 0..dim {
            let qv = q[j];
 let cv = c[offset + j];
            dot += qv * cv;
            norm_c += cv * cv;
        }
        
        let c_norm = norm_c.sqrt();
        if c_norm > 0.0 {
            scores[i] = dot / (q_norm * c_norm);
        }
    }

    let result = env.new_float_array(n as i32).unwrap();
    env.set_float_array_region(&result, 0, &scores).unwrap();
    result.into_raw()
}

/// JNI 接口：批量 inner product 计算
#[no_mangle]
pub extern "system" fn Java_com_rag_sim_VecSimNative_innerProductBatch(
    mut env: JNIEnv, _class: JClass,
    query: JFloatArray,
    corpus: JFloatArray,
    dim: i32,
) -> jfloatArray {
    let dim = dim as usize;
    
    let q = match unsafe { env.get_array_elements(&query, ReleaseMode::NoCopyBack) } {
        Ok(q) => q,
        Err(_) => return env.new_float_array(0).unwrap().into_raw(),
    };
    let c = match unsafe { env.get_array_elements(&corpus, ReleaseMode::NoCopyBack) } {
        Ok(c) => c,
        Err(_) => return env.new_float_array(0).unwrap().into_raw(),
    };

    let corpus_len = c.len();
    let n = corpus_len / dim;
    
    let mut scores = vec![0.0f32; n];

    for i in 0..n {
        let offset = i * dim;
        let mut dot: f32 = 0.0;
        for j in 0..dim {
            dot += q[j] * c[offset + j];
        }
        scores[i] = dot;
    }

    let result = env.new_float_array(n as i32).unwrap();
    env.set_float_array_region(&result, 0, &scores).unwrap();
 result.into_raw()
}
