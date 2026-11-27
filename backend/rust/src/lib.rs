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
/// 依托 Rayon 并发与 SIMD 加速，针对千问 1024 维超球面单位向量进行零拷贝计算
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

/// 稀疏 CSR 二分图个性化 PageRank 算子（HippoRAG 2 双轨阻尼迭代）
/// 计算稳态分布 pi^{(k+1)} = alpha * p0 + (1 - alpha) * (P^T * pi^{(k)} + dangling_mass * p0)
#[no_mangle]
pub unsafe extern "C" fn csr_bipartite_ppr_f32(
    row_ptr: *const i32,
    col_idx: *const i32,
    edge_weights: *const f32,
    reset_prob: *const f32,
    num_nodes: usize,
    alpha: f32,
    max_iters: usize,
    tol: f32,
    scores_out: *mut f32,
    residual_out: *mut f32,
) -> i32 {
    if row_ptr.is_null()
        || col_idx.is_null()
        || edge_weights.is_null()
        || reset_prob.is_null()
        || scores_out.is_null()
        || residual_out.is_null()
    {
        return STATUS_ERR_NULL_PTR;
    }
    if num_nodes == 0 || alpha <= 0.0 || alpha >= 1.0 || max_iters == 0 {
        return STATUS_ERR_INVALID_PARAM;
    }

    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let rows = std::slice::from_raw_parts(row_ptr, num_nodes + 1);
        if rows[0] < 0 {
            return STATUS_ERR_INVALID_PARAM;
        }
        let nnz = rows[num_nodes] as usize;
        let cols = std::slice::from_raw_parts(col_idx, nnz);
        let weights = std::slice::from_raw_parts(edge_weights, nnz);
        let p0_raw = std::slice::from_raw_parts(reset_prob, num_nodes);
        let out_pi = std::slice::from_raw_parts_mut(scores_out, num_nodes);

        // 归一化重置概率分布 p0
        let mut p0 = vec![0.0f32; num_nodes];
        let p0_sum: f32 = p0_raw.iter().map(|&v| v.max(0.0)).sum();
        if p0_sum > 1e-12 {
            for i in 0..num_nodes {
                p0[i] = p0_raw[i].max(0.0) / p0_sum;
            }
        } else {
            let uniform = 1.0f32 / (num_nodes as f32);
            p0.fill(uniform);
        }

        // 预计算每个源节点的出边权重和
        let mut out_weight_sum = vec![0.0f32; num_nodes];
        for u in 0..num_nodes {
            let start = rows[u] as usize;
            let end = rows[u + 1] as usize;
            if end < start || end > nnz {
                return STATUS_ERR_INVALID_PARAM;
            }
            let mut sum_w = 0.0f32;
            for idx in start..end {
                let v = cols[idx];
                if v < 0 || (v as usize) >= num_nodes {
                    return STATUS_ERR_INVALID_PARAM;
                }
                sum_w += weights[idx].max(0.0);
            }
            out_weight_sum[u] = sum_w;
        }

        let mut curr = p0.clone();
        let mut next = vec![0.0f32; num_nodes];
        let mut last_residual = 1.0f32;

        for _ in 0..max_iters {
            let mut dangling_mass = 0.0f32;
            for u in 0..num_nodes {
                if out_weight_sum[u] <= 1e-12 {
                    dangling_mass += curr[u];
                }
            }

            let base_scale = alpha + (1.0 - alpha) * dangling_mass;
            for i in 0..num_nodes {
                next[i] = base_scale * p0[i];
            }

            for u in 0..num_nodes {
                let sum_w = out_weight_sum[u];
                if sum_w > 1e-12 {
                    let coeff = (1.0 - alpha) * curr[u] / sum_w;
                    let start = rows[u] as usize;
                    let end = rows[u + 1] as usize;
                    for idx in start..end {
                        let v = cols[idx] as usize;
                        next[v] += coeff * weights[idx].max(0.0);
                    }
                }
            }

            let mut l1_diff = 0.0f32;
            for i in 0..num_nodes {
                l1_diff += (next[i] - curr[i]).abs();
                curr[i] = next[i];
            }
            last_residual = l1_diff;
            if l1_diff <= tol {
                break;
            }
        }

        out_pi.copy_from_slice(&curr);
        *residual_out = last_residual;
        STATUS_SUCCESS
    }));

    match result {
        Ok(status) => status,
        Err(_) => STATUS_ERR_PANIC,
    }
}

/// S^1023 超球面 von Mises-Fisher (vMF) 方向核权重批量计算算子
/// 计算 w_m = exp(kappa * (<q, mu_m> - 1)) <= 1.0，天然杜绝指数上溢
#[no_mangle]
pub unsafe extern "C" fn vmf_kernel_weights_f32(
    query_ptr: *const f32,
    centroids_ptr: *const f32,
    num_centroids: usize,
    dim: usize,
    kappa: f32,
    weights_out: *mut f32,
) -> i32 {
    if query_ptr.is_null() || centroids_ptr.is_null() || weights_out.is_null() {
        return STATUS_ERR_NULL_PTR;
    }
    if num_centroids == 0 || dim == 0 || kappa < 0.0 {
        return STATUS_ERR_INVALID_PARAM;
    }

    let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let q = std::slice::from_raw_parts(query_ptr, dim);
        let centroids = std::slice::from_raw_parts(centroids_ptr, num_centroids * dim);
        let out = std::slice::from_raw_parts_mut(weights_out, num_centroids);

        out.par_iter_mut().enumerate().for_each(|(m, w)| {
            let c_slice = &centroids[m * dim..(m + 1) * dim];
            let cos_sim = dot_product(q, c_slice, dim).clamp(-1.0, 1.0);
            *w = (kappa * (cos_sim - 1.0)).exp();
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
        let dim = 1024;
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

    #[test]
    fn test_csr_bipartite_ppr_convergence() {
        // 3 节点链 0 -> 1 -> 2
        let row_ptr = vec![0i32, 1, 2, 2];
        let col_idx = vec![1i32, 2];
        let weights = vec![1.0f32, 1.0];
        let reset = vec![1.0f32, 0.0, 0.0];
        let mut scores = vec![0.0f32; 3];
        let mut residual = 1.0f32;

        let status = unsafe {
            csr_bipartite_ppr_f32(
                row_ptr.as_ptr(),
                col_idx.as_ptr(),
                weights.as_ptr(),
                reset.as_ptr(),
                3,
                0.15,
                100,
                1e-6,
                scores.as_mut_ptr(),
                &mut residual,
            )
        };
        assert_eq!(status, STATUS_SUCCESS);
        assert!(residual <= 1e-5);
        let sum: f32 = scores.iter().sum();
        assert!((sum - 1.0).abs() < 1e-4, "PPR 稳态概率质量总和必须等于 1.0");
        assert!(scores[0] > scores[1] && scores[1] > scores[2]);
    }

    #[test]
    fn test_vmf_kernel_weights_bounds() {
        let dim = 1024;
        let mut q = vec![0.0f32; dim];
        q[0] = 1.0;
        let mut centroids = vec![0.0f32; 2 * dim];
        centroids[0] = 1.0; // 与 q 重合，cos = 1.0 -> weight = 1.0
        centroids[dim + 1] = 1.0; // 与 q 正交，cos = 0.0 -> weight = exp(-kappa)
        let mut weights = vec![0.0f32; 2];

        let status = unsafe {
            vmf_kernel_weights_f32(q.as_ptr(), centroids.as_ptr(), 2, dim, 4.0, weights.as_mut_ptr())
        };
        assert_eq!(status, STATUS_SUCCESS);
        assert!((weights[0] - 1.0).abs() < 1e-6);
        assert!((weights[1] - (-4.0f32).exp()).abs() < 1e-5);
    }
}
