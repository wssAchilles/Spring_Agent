use jni::objects::ReleaseMode;
use jni::objects::{JByteBuffer, JClass, JFloatArray};
use jni::sys::{jfloatArray, jint};
use jni::JNIEnv;
use rayon::prelude::*;
use std::panic::{catch_unwind, AssertUnwindSafe};
use std::ptr;

#[cfg(target_arch = "x86_64")]
use std::arch::x86_64::*;

#[cfg(target_arch = "aarch64")]
use std::arch::aarch64::*;

// [溯源] 千问 1024 维超球面向量与通用长度点积核心算法 (SIMD 硬件加速 + FFI 零越界安全保护)

/// 计算两个相同维度向量的点积（动态硬件 SIMD 派发，原生支持 1024 维超球面主基线与任意维度）
#[inline(always)]
pub fn dot_product_simd(a: &[f32], b: &[f32]) -> f32 {
    let len = a.len().min(b.len());
    if len == 0 {
        return 0.0;
    }

    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            return unsafe { dot_product_avx2_fma(a.as_ptr(), b.as_ptr(), len) };
        }
    }

    #[cfg(target_arch = "aarch64")]
    {
        return unsafe { dot_product_neon(a.as_ptr(), b.as_ptr(), len) };
    }

    #[allow(unreachable_code)]
    dot_product_generic(&a[..len], &b[..len])
}

/// 千问 1024 维专用点积入口
#[inline(always)]
pub fn dot_product_1024(a: &[f32], b: &[f32]) -> f32 {
    debug_assert!(a.len() >= 1024 && b.len() >= 1024);
    dot_product_simd(&a[..1024], &b[..1024])
}

/// x86_64: AVX2 + FMA 8路展开 (1024 维恰好 16 次 64-float 迭代，尾部自适应回退)
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2,fma")]
pub unsafe fn dot_product_avx2_fma(a: *const f32, b: *const f32, len: usize) -> f32 {
    let mut acc0 = _mm256_setzero_ps();
    let mut acc1 = _mm256_setzero_ps();
    let mut acc2 = _mm256_setzero_ps();
    let mut acc3 = _mm256_setzero_ps();
    let mut acc4 = _mm256_setzero_ps();
    let mut acc5 = _mm256_setzero_ps();
    let mut acc6 = _mm256_setzero_ps();
    let mut acc7 = _mm256_setzero_ps();

    let mut i = 0usize;
    while i + 64 <= len {
        acc0 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i)), _mm256_loadu_ps(b.add(i)), acc0);
        acc1 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i + 8)), _mm256_loadu_ps(b.add(i + 8)), acc1);
        acc2 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i + 16)), _mm256_loadu_ps(b.add(i + 16)), acc2);
        acc3 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i + 24)), _mm256_loadu_ps(b.add(i + 24)), acc3);
        acc4 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i + 32)), _mm256_loadu_ps(b.add(i + 32)), acc4);
        acc5 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i + 40)), _mm256_loadu_ps(b.add(i + 40)), acc5);
        acc6 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i + 48)), _mm256_loadu_ps(b.add(i + 48)), acc6);
        acc7 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i + 56)), _mm256_loadu_ps(b.add(i + 56)), acc7);
        i += 64;
    }

    while i + 8 <= len {
        acc0 = _mm256_fmadd_ps(_mm256_loadu_ps(a.add(i)), _mm256_loadu_ps(b.add(i)), acc0);
        i += 8;
    }

    let sum01 = _mm256_add_ps(acc0, acc1);
    let sum23 = _mm256_add_ps(acc2, acc3);
    let sum45 = _mm256_add_ps(acc4, acc5);
    let sum67 = _mm256_add_ps(acc6, acc7);

    let sum0123 = _mm256_add_ps(sum01, sum23);
    let sum4567 = _mm256_add_ps(sum45, sum67);
    let sum = _mm256_add_ps(sum0123, sum4567);

    let hi128 = _mm256_extractf128_ps(sum, 1);
    let lo128 = _mm256_castps256_ps128(sum);
    let sum128 = _mm_add_ps(lo128, hi128);
    let shuf = _mm_movehdup_ps(sum128);
    let sums = _mm_add_ps(sum128, shuf);
    let shuf2 = _mm_movehl_ps(sums, sums);
    let res = _mm_add_ss(sums, shuf2);

    let mut total = _mm_cvtss_f32(res);
    while i < len {
        total += *a.add(i) * *b.add(i);
        i += 1;
    }
    total
}

/// aarch64: NEON 4路展开 (1024 维恰好 64 次 16-float 迭代，尾部自适应回退)
#[cfg(target_arch = "aarch64")]
pub unsafe fn dot_product_neon(a: *const f32, b: *const f32, len: usize) -> f32 {
    let mut acc0 = vdupq_n_f32(0.0);
    let mut acc1 = vdupq_n_f32(0.0);
    let mut acc2 = vdupq_n_f32(0.0);
    let mut acc3 = vdupq_n_f32(0.0);

    let mut i = 0usize;
    while i + 16 <= len {
        acc0 = vfmaq_f32(acc0, vld1q_f32(a.add(i)), vld1q_f32(b.add(i)));
        acc1 = vfmaq_f32(acc1, vld1q_f32(a.add(i + 4)), vld1q_f32(b.add(i + 4)));
        acc2 = vfmaq_f32(acc2, vld1q_f32(a.add(i + 8)), vld1q_f32(b.add(i + 8)));
        acc3 = vfmaq_f32(acc3, vld1q_f32(a.add(i + 12)), vld1q_f32(b.add(i + 12)));
        i += 16;
    }

    while i + 4 <= len {
        acc0 = vfmaq_f32(acc0, vld1q_f32(a.add(i)), vld1q_f32(b.add(i)));
        i += 4;
    }

    let sum01 = vaddq_f32(acc0, acc1);
    let sum23 = vaddq_f32(acc2, acc3);
    let sum = vaddq_f32(sum01, sum23);

    let mut total = vaddvq_f32(sum);
    while i < len {
        total += *a.add(i) * *b.add(i);
        i += 1;
    }
    total
}

/// 通用标量点积实现
#[inline(always)]
pub fn dot_product_generic(a: &[f32], b: &[f32]) -> f32 {
    let len = a.len().min(b.len());
    let mut sum = 0.0f32;
    let mut i = 0;
    while i + 4 <= len {
        sum += a[i] * b[i]
            + a[i + 1] * b[i + 1]
            + a[i + 2] * b[i + 2]
            + a[i + 3] * b[i + 3];
        i += 4;
    }
    while i < len {
        sum += a[i] * b[i];
        i += 1;
    }
    sum
}

/// 计算 L2 范数平方
#[inline(always)]
fn l2_norm_sq(v: &[f32]) -> f32 {
    dot_product_simd(v, v)
}

// 批量点积计算引擎 (自适应分块 Rayon 并行)
pub fn inner_product_batch_scores(query: &[f32], corpus: &[f32], dim: usize) -> Vec<f32> {
    if dim == 0 || query.len() != dim || corpus.len() % dim != 0 {
        return Vec::new();
    }
    let n = corpus.len() / dim;
    if n == 0 {
        return Vec::new();
    }

    if n >= 128 {
        corpus
            .par_chunks_exact(dim)
            .map(|chunk| dot_product_simd(query, chunk))
            .collect()
    } else {
        let mut scores = Vec::with_capacity(n);
        for chunk in corpus.chunks_exact(dim) {
            scores.push(dot_product_simd(query, chunk));
        }
        scores
    }
}

// 批量余弦相似度计算引擎（原生激活 1024 维千问超球面单位向量免开方快路径）
pub fn cosine_batch_scores(query: &[f32], corpus: &[f32], dim: usize) -> Vec<f32> {
    if dim == 0 || query.len() != dim || corpus.len() % dim != 0 {
        return Vec::new();
    }
    let n = corpus.len() / dim;
    if n == 0 {
        return Vec::new();
    }

    let q_norm_sq = l2_norm_sq(query);
    if q_norm_sq <= 0.0 {
        return vec![0.0f32; n];
    }
    let q_norm = q_norm_sq.sqrt();

    // 针对阿里千问 1024 维归一化超球面向量的快速路径：如果 query 模长接近 1.0 (误差 < 1e-4)
    let is_unit_query = (q_norm_sq - 1.0).abs() < 1e-4;

    let compute_score = |chunk: &[f32]| -> f32 {
        let dot = dot_product_simd(query, chunk);
        if is_unit_query {
            let c_norm_sq = l2_norm_sq(chunk);
            if (c_norm_sq - 1.0).abs() < 1e-4 {
                return dot; // 均已归一化，直接返回超球面余弦内积
            }
            let c_norm = c_norm_sq.sqrt();
            if c_norm > 0.0 {
                return dot / c_norm;
            }
        } else {
            let c_norm = l2_norm_sq(chunk).sqrt();
            if c_norm > 0.0 {
                return dot / (q_norm * c_norm);
            }
        }
        0.0
    };

    if n >= 128 {
        corpus.par_chunks_exact(dim).map(compute_score).collect()
    } else {
        let mut scores = Vec::with_capacity(n);
        for chunk in corpus.chunks_exact(dim) {
            scores.push(compute_score(chunk));
        }
        scores
    }
}

fn empty_float_array(env: &mut JNIEnv) -> jfloatArray {
    match env.new_float_array(0) {
        Ok(array) => array.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn float_array(env: &mut JNIEnv, values: &[f32]) -> jfloatArray {
    if values.len() > i32::MAX as usize {
        return empty_float_array(env);
    }
    let out = match env.new_float_array(values.len() as i32) {
        Ok(array) => array,
        Err(_) => return ptr::null_mut(),
    };
    if env.set_float_array_region(&out, 0, values).is_err() {
        return empty_float_array(env);
    }
    out.into_raw()
}

fn array_len(env: &mut JNIEnv, array: &JFloatArray) -> Option<usize> {
    if array.is_null() {
        return None;
    }
    let len = env.get_array_length(array).ok()?;
    usize::try_from(len).ok()
}

/// 校验 DirectByteBuffer 容量是否满足所需 float 元素总数（防整数溢出与越界读取 SIGSEGV）
fn validate_direct_buffer(env: &JNIEnv, buf: &JByteBuffer, required_floats: usize) -> Option<*const f32> {
    if buf.is_null() {
        return None;
    }
    let required_bytes = required_floats.checked_mul(std::mem::size_of::<f32>())?;
    let capacity = env.get_direct_buffer_capacity(buf).ok()?;
    if capacity < required_bytes {
        return None;
    }
    let ptr = env.get_direct_buffer_address(buf).ok()?;
    if ptr.is_null() || (ptr as usize) % std::mem::align_of::<f32>() != 0 {
        return None;
    }
    Some(ptr as *const f32)
}

/// JNI 接口 1: Java float[] 数组版批量余弦计算
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_sim_VecSimNative_cosineBatch(
    mut env: JNIEnv,
    _class: JClass,
    query: JFloatArray,
    corpus: JFloatArray,
    dim: jint,
) -> jfloatArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if dim <= 0 {
            return empty_float_array(&mut env);
        }
        let dim = dim as usize;
        if array_len(&mut env, &query) != Some(dim) {
            return empty_float_array(&mut env);
        }
        let corpus_len = match array_len(&mut env, &corpus) {
            Some(len) if len > 0 && len % dim == 0 => len,
            _ => return empty_float_array(&mut env),
        };

        let scores = {
            let q = match unsafe { env.get_array_elements(&query, ReleaseMode::NoCopyBack) } {
                Ok(q) => q,
                Err(_) => return empty_float_array(&mut env),
            };
            let c = match unsafe { env.get_array_elements(&corpus, ReleaseMode::NoCopyBack) } {
                Ok(c) => c,
                Err(_) => return empty_float_array(&mut env),
            };
            let n = corpus_len / dim;
            cosine_batch_scores(&q[..dim], &c[..n * dim], dim)
        };

        float_array(&mut env, &scores)
    }));
    result.unwrap_or(ptr::null_mut())
}

/// JNI 接口 2: Java float[] 数组版批量点积计算
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_sim_VecSimNative_innerProductBatch(
    mut env: JNIEnv,
    _class: JClass,
    query: JFloatArray,
    corpus: JFloatArray,
    dim: jint,
) -> jfloatArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if dim <= 0 {
            return empty_float_array(&mut env);
        }
        let dim = dim as usize;
        if array_len(&mut env, &query) != Some(dim) {
            return empty_float_array(&mut env);
        }
        let corpus_len = match array_len(&mut env, &corpus) {
            Some(len) if len > 0 && len % dim == 0 => len,
            _ => return empty_float_array(&mut env),
        };

        let scores = {
            let q = match unsafe { env.get_array_elements(&query, ReleaseMode::NoCopyBack) } {
                Ok(q) => q,
                Err(_) => return empty_float_array(&mut env),
            };
            let c = match unsafe { env.get_array_elements(&corpus, ReleaseMode::NoCopyBack) } {
                Ok(c) => c,
                Err(_) => return empty_float_array(&mut env),
            };
            let n = corpus_len / dim;
            inner_product_batch_scores(&q[..dim], &c[..n * dim], dim)
        };

        float_array(&mut env, &scores)
    }));
    result.unwrap_or(ptr::null_mut())
}

/// JNI 接口 3: 堆外直接内存 (DirectByteBuffer) 零拷贝 cosine 计算（带容量与对齐边界守卫）
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_sim_VecSimNative_cosineBatchDirect(
    mut env: JNIEnv,
    _class: JClass,
    query_buf: JByteBuffer,
    corpus_buf: JByteBuffer,
    dim: jint,
    n: jint,
) -> jfloatArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if dim <= 0 || n <= 0 {
            return empty_float_array(&mut env);
        }
        let dim = dim as usize;
        let n = n as usize;
        let total_corpus_floats = match n.checked_mul(dim) {
            Some(total) => total,
            None => return empty_float_array(&mut env),
        };

        let query_ptr = match validate_direct_buffer(&env, &query_buf, dim) {
            Some(p) => p,
            None => return empty_float_array(&mut env),
        };
        let corpus_ptr = match validate_direct_buffer(&env, &corpus_buf, total_corpus_floats) {
            Some(p) => p,
            None => return empty_float_array(&mut env),
        };

        let q_slice = unsafe { std::slice::from_raw_parts(query_ptr, dim) };
        let c_slice = unsafe { std::slice::from_raw_parts(corpus_ptr, total_corpus_floats) };

        let scores = cosine_batch_scores(q_slice, c_slice, dim);
        float_array(&mut env, &scores)
    }));
    result.unwrap_or(ptr::null_mut())
}

/// JNI 接口 4: 堆外直接内存 (DirectByteBuffer) 零拷贝 inner product 计算（带容量与对齐边界守卫）
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_sim_VecSimNative_innerProductBatchDirect(
    mut env: JNIEnv,
    _class: JClass,
    query_buf: JByteBuffer,
    corpus_buf: JByteBuffer,
    dim: jint,
    n: jint,
) -> jfloatArray {
    let result = catch_unwind(AssertUnwindSafe(|| {
        if dim <= 0 || n <= 0 {
            return empty_float_array(&mut env);
        }
        let dim = dim as usize;
        let n = n as usize;
        let total_corpus_floats = match n.checked_mul(dim) {
            Some(total) => total,
            None => return empty_float_array(&mut env),
        };

        let query_ptr = match validate_direct_buffer(&env, &query_buf, dim) {
            Some(p) => p,
            None => return empty_float_array(&mut env),
        };
        let corpus_ptr = match validate_direct_buffer(&env, &corpus_buf, total_corpus_floats) {
            Some(p) => p,
            None => return empty_float_array(&mut env),
        };

        let q_slice = unsafe { std::slice::from_raw_parts(query_ptr, dim) };
        let c_slice = unsafe { std::slice::from_raw_parts(corpus_ptr, total_corpus_floats) };

        let scores = inner_product_batch_scores(q_slice, c_slice, dim);
        float_array(&mut env, &scores)
    }));
    result.unwrap_or(ptr::null_mut())
}

// --------------------------- 单元测试 ---------------------------

#[cfg(test)]
mod tests {
    use super::*;

    fn assert_close(actual: f32, expected: f32) {
        let diff = (actual - expected).abs();
        let rel_diff = diff / expected.abs().max(1.0);
        assert!(rel_diff < 1e-4, "actual={actual}, expected={expected}, rel_diff={rel_diff}");
    }

    #[test]
    fn test_dot_product_1024_precision() {
        let mut q = vec![0.0f32; 1024];
        let mut c = vec![0.0f32; 1024];
        for i in 0..1024 {
            q[i] = (i as f32 % 10.0) / 10.0;
            c[i] = ((i + 3) as f32 % 10.0) / 10.0;
        }

        // 模拟阿里千问 text-embedding-v4 1024 维超球面单位向量
        let q_norm = q.iter().map(|x| x * x).sum::<f32>().sqrt();
        let c_norm = c.iter().map(|x| x * x).sum::<f32>().sqrt();
        for i in 0..1024 {
            q[i] /= q_norm;
            c[i] /= c_norm;
        }

        let expected: f32 = q.iter().zip(c.iter()).map(|(x, y)| x * y).sum();
        let actual = dot_product_1024(&q, &c);

        assert_close(actual, expected);
        assert!((actual - expected).abs() < 1e-4);

        let batch_cos = cosine_batch_scores(&q, &c, 1024);
        assert_eq!(batch_cos.len(), 1);
        assert!((batch_cos[0] - expected).abs() < 1e-4);
    }

    #[test]
    fn test_cosine_batch_scores_handles_match_and_orthogonal() {
        let query = [1.0, 0.0];
        let corpus = [1.0, 0.0, 0.0, 1.0, 0.0, 0.0];

        let scores = cosine_batch_scores(&query, &corpus, 2);

        assert_eq!(scores.len(), 3);
        assert_close(scores[0], 1.0);
        assert_close(scores[1], 0.0);
        assert_close(scores[2], 0.0);
    }

    #[test]
    fn test_inner_product_batch_scores_returns_dot_products() {
        let query = [2.0, 3.0];
        let corpus = [4.0, 5.0, -1.0, 2.0];

        let scores = inner_product_batch_scores(&query, &corpus, 2);

        assert_eq!(scores, vec![23.0, 4.0]);
    }

    #[test]
    fn test_large_batch_rayon_parallel_1024() {
        let dim = 1024;
        let n = 200; // 触发 Rayon 分块并行
        let q = vec![0.05f32; dim];
        let c = vec![0.02f32; dim * n];

        let scores = inner_product_batch_scores(&q, &c, dim);
        assert_eq!(scores.len(), n);

        let single_expected = dim as f32 * 0.05 * 0.02;
        for s in scores {
            assert_close(s, single_expected);
        }
    }
}
