use rayon::prelude::*;

#[inline]
#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2,fma")]
unsafe fn dot_product_avx2(q: &[f32], d: &[f32], dim: usize) -> f32 {
    use std::arch::x86_64::*;
    let q_ptr = q.as_ptr();
    let d_ptr = d.as_ptr();
    let mut sum_vec = _mm256_setzero_ps();
    let mut i = 0;
    while i + 8 <= dim {
        let q_vec = _mm256_loadu_ps(q_ptr.add(i));
        let d_vec = _mm256_loadu_ps(d_ptr.add(i));
        sum_vec = _mm256_fmadd_ps(q_vec, d_vec, sum_vec);
        i += 8;
    }
    let mut arr = [0.0f32; 8];
    _mm256_storeu_ps(arr.as_mut_ptr(), sum_vec);
    let mut sum = arr[0] + arr[1] + arr[2] + arr[3] + arr[4] + arr[5] + arr[6] + arr[7];
    while i < dim {
        sum += (*q_ptr.add(i)) * (*d_ptr.add(i));
        i += 1;
    }
    sum
}

#[inline]
unsafe fn dot_product_scalar(q: &[f32], d: &[f32], dim: usize) -> f32 {
    let mut sum = 0.0;
    for i in 0..dim {
        sum += q[i] * d[i];
    }
    sum
}

#[no_mangle]
pub unsafe extern "C" fn maxsim_batch(
    query_ptr: *const f32,
    query_len: usize,
    doc_ptr: *const f32,
    doc_lens_ptr: *const i32,
    num_docs: usize,
    dim: usize,
    scores_out: *mut f32,
) {
    let doc_lens = std::slice::from_raw_parts(doc_lens_ptr, num_docs);
    
    let mut offsets = Vec::with_capacity(num_docs);
    let mut current_offset = 0;
    for &len in doc_lens {
        offsets.push(current_offset);
        current_offset += len as usize * dim;
    }
    let total_doc_floats = current_offset;
    
    let query_slice = std::slice::from_raw_parts(query_ptr, query_len * dim);
    let doc_slice = std::slice::from_raw_parts(doc_ptr, total_doc_floats);
    let scores = std::slice::from_raw_parts_mut(scores_out, num_docs);
    
    #[cfg(target_arch = "x86_64")]
    let use_avx2 = std::arch::is_x86_feature_detected!("avx2") && std::arch::is_x86_feature_detected!("fma");
    #[cfg(not(target_arch = "x86_64"))]
    let use_avx2 = false;

    scores.par_iter_mut().enumerate().for_each(|(d, score)| {
        let doc_start = offsets[d];
        let doc_len = doc_lens[d] as usize;
        let mut total_score = 0.0;
        
        for i in 0..query_len {
            let q_vec = &query_slice[i * dim .. (i + 1) * dim];
            let mut max_sim = f32::NEG_INFINITY;
            
            for j in 0..doc_len {
                let d_vec = &doc_slice[doc_start + j * dim .. doc_start + (j + 1) * dim];
                let sim;
                #[cfg(target_arch = "x86_64")]
                {
                    if use_avx2 {
                        sim = dot_product_avx2(q_vec, d_vec, dim);
                    } else {
                        sim = dot_product_scalar(q_vec, d_vec, dim);
                    }
                }
                #[cfg(not(target_arch = "x86_64"))]
                {
                    sim = dot_product_scalar(q_vec, d_vec, dim);
                }
                
                if sim > max_sim {
                    max_sim = sim;
                }
            }
            total_score += max_sim;
        }
        *score = total_score;
    });
}
