use rayon::prelude::*;

// 错误码常量定义
pub const STATUS_SUCCESS: i32 = 0;
pub const STATUS_ERR_NULL_PTR: i32 = -1;
pub const STATUS_ERR_INVALID_PARAM: i32 = -2;
pub const STATUS_ERR_PANIC: i32 = -3;

pub const ARCH_SCALAR: i32 = 0;
pub const ARCH_AVX2: i32 = 1;
pub const ARCH_NEON: i32 = 2;

/// ARM64 NEON SIMD 向量点积优化实现
#[inline]
#[cfg(target_arch = "aarch64")]
unsafe fn dot_product_neon(q: &[f32], d: &[f32], dim: usize) -> f32 {
    use std::arch::aarch64::*;
    let mut sum_vec = vdupq_n_f32(0.0);
    let mut i = 0;
    while i + 4 <= dim {
        let q_vec = vld1q_f32(q.as_ptr().add(i));
        let d_vec = vld1q_f32(d.as_ptr().add(i));
        sum_vec = vfmaq_f32(sum_vec, q_vec, d_vec);
        i += 4;
    }
    let mut sum = vaddvq_f32(sum_vec);
    while i < dim {
        sum += *q.as_ptr().add(i) * *d.as_ptr().add(i);
        i += 1;
    }
    sum
}

/// x86_64 AVX2 + FMA SIMD 向量点积优化实现
#[inline]
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2,fma")]
unsafe fn dot_product_avx2(q: &[f32], d: &[f32], dim: usize) -> f32 {
    use std::arch::x86_64::*;
    let mut sum_vec = _mm256_setzero_ps();
    let mut i = 0;
    while i + 8 <= dim {
        let q_vec = _mm256_loadu_ps(q.as_ptr().add(i));
        let d_vec = _mm256_loadu_ps(d.as_ptr().add(i));
        sum_vec = _mm256_fmadd_ps(q_vec, d_vec, sum_vec);
        i += 8;
    }
    let mut arr = [0.0f32; 8];
    _mm256_storeu_ps(arr.as_mut_ptr(), sum_vec);
    let mut sum = arr[0] + arr[1] + arr[2] + arr[3] + arr[4] + arr[5] + arr[6] + arr[7];
    while i < dim {
        sum += *q.as_ptr().add(i) * *d.as_ptr().add(i);
        i += 1;
    }
    sum
}

/// 标量兜底点积计算
#[inline]
#[allow(dead_code)]
fn dot_product_scalar(q: &[f32], d: &[f32], dim: usize) -> f32 {
    let mut sum = 0.0f32;
    for i in 0..dim {
        sum += q[i] * d[i];
    }
    sum
}

/// 架构自适应点积派发函数
#[inline]
unsafe fn dot_product(q: &[f32], d: &[f32], dim: usize) -> f32 {
    #[cfg(target_arch = "aarch64")]
    {
        dot_product_neon(q, d, dim)
    }
    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            dot_product_avx2(q, d, dim)
        } else {
            dot_product_scalar(q, d, dim)
        }
    }
    #[cfg(not(any(target_arch = "aarch64", target_arch = "x86_64")))]
    {
        dot_product_scalar(q, d, dim)
    }
}

/// 探测当前生效的底层 SIMD 硬件架构
#[no_mangle]
pub extern "C" fn get_simd_architecture() -> i32 {
    #[cfg(target_arch = "aarch64")]
    {
        ARCH_NEON
    }
    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            ARCH_AVX2
        } else {
            ARCH_SCALAR
        }
    }
    #[cfg(not(any(target_arch = "aarch64", target_arch = "x86_64")))]
    {
        ARCH_SCALAR
    }
}

/// 多文档 ColBERT MaxSim 批量打分算子
/// 依托 Rayon 并发与 SIMD 加速，针对千问 1536 维超球面单位向量进行零拷贝计算
#[no_mangle]
pub unsafe extern "C" fn maxsim_batch(
    query_ptr: *const f32,
    query_len: usize,
    doc_ptr: *const f32,
    doc_lens_ptr: *const i32,
    num_docs: usize,
    dim: usize,
    scores_out: *mut f32,
) -> i32 {
    // 1. 指针防空卫语句检查
    if query_ptr.is_null() || doc_ptr.is_null() || doc_lens_ptr.is_null() || scores_out.is_null() {
        return STATUS_ERR_NULL_PTR;
    }
    if query_len == 0 || num_docs == 0 || dim == 0 {
        return STATUS_ERR_INVALID_PARAM;
    }

    // 2. 隔离栈展开保护，防止 panic 跨越 C-ABI 边界导致 JVM 崩溃
    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let doc_lens = std::slice::from_raw_parts(doc_lens_ptr, num_docs);
        
        let mut offsets = Vec::with_capacity(num_docs);
        let mut current_offset = 0usize;
        for &len in doc_lens {
            if len <= 0 {
                return STATUS_ERR_INVALID_PARAM;
            }
            offsets.push(current_offset);
            current_offset += len as usize * dim;
        }
        let total_doc_floats = current_offset;
        
        let query_slice = std::slice::from_raw_parts(query_ptr, query_len * dim);
        let doc_slice = std::slice::from_raw_parts(doc_ptr, total_doc_floats);
        let scores = std::slice::from_raw_parts_mut(scores_out, num_docs);

        // Rayon 多核并行计算各文档 MaxSim 得分
        scores.par_iter_mut().enumerate().for_each(|(d, score)| {
            let doc_start = offsets[d];
            let doc_len = doc_lens[d] as usize;
            let mut total_score = 0.0f32;
            
            for i in 0..query_len {
                let q_vec = &query_slice[i * dim .. (i + 1) * dim];
                let mut max_sim = f32::NEG_INFINITY;
                
                for j in 0..doc_len {
                    let d_vec = &doc_slice[doc_start + j * dim .. doc_start + (j + 1) * dim];
                    let sim = dot_product(q_vec, d_vec, dim);
                    if sim > max_sim {
                        max_sim = sim;
                    }
                }
                total_score += max_sim;
            }
            *score = total_score;
        });

        STATUS_SUCCESS
    }));

    match result {
        Ok(status) => status,
        Err(_) => STATUS_ERR_PANIC,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_dot_product_consistency() {
        let dim = 1536;
        let mut q = vec![0.0f32; dim];
        let mut d = vec![0.0f32; dim];
        for i in 0..dim {
            q[i] = (i as f32 % 7.0) / 7.0;
            d[i] = (i as f32 % 11.0) / 11.0;
        }

        let scalar_result = dot_product_scalar(&q, &d, dim);
        let sim = unsafe { dot_product(&q, &d, dim) };
        let relative_error = (scalar_result - sim).abs() / scalar_result.abs();
        assert!(relative_error < 1e-4, "SIMD 点积结果相对误差必须小于万分之一");
    }

    #[test]
    fn test_maxsim_batch_valid() {
        let dim = 4;
        let query = vec![
            1.0f32, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
        ];
        let query_len = 2;

        let docs = vec![
            1.0f32, 0.0, 0.0, 0.0,
            0.0, 1.0, 0.0, 0.0,
            0.0, 0.0, 1.0, 0.0,

            0.5f32, 0.5, 0.0, 0.0,
            0.0, 0.0, 0.0, 1.0,
        ];
        let doc_lens = vec![3i32, 2i32];
        let num_docs = 2;
        let mut scores = vec![0.0f32; num_docs];

        let status = unsafe {
            maxsim_batch(
                query.as_ptr(),
                query_len,
                docs.as_ptr(),
                doc_lens.as_ptr(),
                num_docs,
                dim,
                scores.as_mut_ptr(),
            )
        };

        assert_eq!(status, STATUS_SUCCESS);
        assert!((scores[0] - 2.0).abs() < 1e-4);
        assert!((scores[1] - 1.0).abs() < 1e-4);
    }

    #[test]
    fn test_maxsim_batch_null_pointer() {
        let mut scores = vec![0.0f32; 1];
        let status = unsafe {
            maxsim_batch(
                std::ptr::null(),
                1,
                std::ptr::null(),
                std::ptr::null(),
                1,
                4,
                scores.as_mut_ptr(),
            )
        };
        assert_eq!(status, STATUS_ERR_NULL_PTR);
    }
}
