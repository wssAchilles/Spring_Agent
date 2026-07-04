use jni::JNIEnv;
use jni::objects::{JClass, JString, JObject, JObjectArray};
use jni::sys::jobjectArray;
use jieba_rs::Jieba;
use lazy_static::lazy_static;

lazy_static! {
    static ref JIEBA: Jieba = Jieba::new();
}

/// JNI 接口：中文分词
/// 返回 String[] 数组
#[no_mangle]
pub extern "system" fn Java_com_rag_nlp_JiebaNative_cut(
    mut env: JNIEnv, _class: JClass, input: JString
) -> jobjectArray {
    let text: String = env.get_string(&input).unwrap().into();
    let words = JIEBA.cut(&text, false);  // 精确模式

    let word_class = env.find_class("java/lang/String").unwrap();
    let result = env.new_object_array(words.len() as i32, word_class, JObject::null()).unwrap();
    for (i, word) in words.iter().enumerate() {
        let jword = env.new_string(word).unwrap();
        env.set_object_array_element(&result, i as i32, jword).unwrap();
    }
    result.into_raw()
}

/// JNI 接口：中文分词 + 词性标注
/// 返回包含 "word|pos" 格式的 String[]
#[no_mangle]
pub extern "system" fn Java_com_rag_nlp_JiebaNative_cutWithPos(
    mut env: JNIEnv, _class: JClass, input: JString
) -> jobjectArray {
    let text: String = env.get_string(&input).unwrap().into();
    let tags = JIEBA.tag(&text, false);

    let word_class = env.find_class("java/lang/String").unwrap();
    let result = env.new_object_array(tags.len() as i32, word_class, JObject::null()).unwrap();
    for (i, tag) in tags.iter().enumerate() {
        let word_with_pos = format!("{}|{}", tag.word, tag.tag);
        let jword = env.new_string(&word_with_pos).unwrap();
        env.set_object_array_element(&result, i as i32, jword).unwrap();
    }
    result.into_raw()
}
