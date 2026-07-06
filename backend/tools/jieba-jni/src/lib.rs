use jieba_rs::Jieba;
use jni::objects::{JClass, JObject, JString};
use jni::sys::jobjectArray;
use jni::JNIEnv;
use lazy_static::lazy_static;
use std::ptr;

lazy_static! {
    static ref JIEBA: Jieba = Jieba::new();
}

fn empty_string_array(env: &mut JNIEnv) -> jobjectArray {
    match env
        .find_class("java/lang/String")
        .and_then(|class| env.new_object_array(0, class, JObject::null()))
    {
        Ok(array) => array.into_raw(),
        Err(_) => ptr::null_mut(),
    }
}

fn string_array<'a, I>(env: &mut JNIEnv, values: I) -> jobjectArray
where
    I: IntoIterator<Item = &'a str>,
    I::IntoIter: ExactSizeIterator,
{
    let values = values.into_iter();
    let len = values.len();
    if len > i32::MAX as usize {
        return empty_string_array(env);
    }
    let word_class = match env.find_class("java/lang/String") {
        Ok(class) => class,
        Err(_) => return ptr::null_mut(),
    };
    let result = match env.new_object_array(len as i32, word_class, JObject::null()) {
        Ok(array) => array,
        Err(_) => return ptr::null_mut(),
    };
    for (i, word) in values.enumerate() {
        let jword = match env.new_string(word) {
            Ok(value) => value,
            Err(_) => return empty_string_array(env),
        };
        if env
            .set_object_array_element(&result, i as i32, jword)
            .is_err()
        {
            return empty_string_array(env);
        }
    }
    result.into_raw()
}

/// JNI 接口：中文分词
/// 返回 String[] 数组
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_nlp_JiebaNative_cut(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jobjectArray {
    let text: String = match env.get_string(&input) {
        Ok(text) => text.into(),
        Err(_) => return empty_string_array(&mut env),
    };
    let words = JIEBA.cut(&text, false); // 精确模式

    string_array(&mut env, words.iter().copied())
}

/// JNI 接口：中文分词 + 词性标注
/// 返回包含 "word|pos" 格式的 String[]
#[no_mangle]
pub extern "system" fn Java_tech_qiantong_qknow_module_kmc_service_rag_nlp_JiebaNative_cutWithPos(
    mut env: JNIEnv,
    _class: JClass,
    input: JString,
) -> jobjectArray {
    let text: String = match env.get_string(&input) {
        Ok(text) => text.into(),
        Err(_) => return empty_string_array(&mut env),
    };
    let tags = JIEBA.tag(&text, false);
    let tagged: Vec<String> = tags
        .iter()
        .map(|tag| format!("{}|{}", tag.word, tag.tag))
        .collect();

    string_array(&mut env, tagged.iter().map(String::as_str))
}
