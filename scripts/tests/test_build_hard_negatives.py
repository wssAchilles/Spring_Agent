import unittest
import sys
import os

# 将 scripts 目录加入路径以便导入
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '..')))

from build_hard_negatives import BM25Scorer, mine_hard_negatives

class TestBuildHardNegatives(unittest.TestCase):
    def setUp(self):
        # 准备测试文档库
        self.documents = [
            {"id": "doc_uuid_1", "text": "Apple is a tech company known for iPhones and MacBooks."},
            {"id": "doc_uuid_2", "text": "An apple a day keeps the doctor away. Apple is a fruit."},
            {"id": "doc_uuid_3", "text": "Apple pie recipe: slice the apple, add sugar, bake."},
            {"id": "doc_uuid_4", "text": "Microsoft Windows is an operating system."},
            {"id": "doc_uuid_5", "text": "Banana is a yellow fruit."},
            {"id": "doc_uuid_6", "text": "Apple tree planting guide. How to grow an apple tree."},
        ]
        
    def test_bm25_scorer(self):
        # 测试 BM25 算分基础逻辑
        scorer = BM25Scorer([doc["text"] for doc in self.documents])
        scores = scorer.get_scores("Apple is a fruit")
        self.assertEqual(len(scores), len(self.documents))
        # 包含字眼的文档得分应该更高
        self.assertTrue(scores[1] > 0)
        
    def test_mine_hard_negatives(self):
        # 模拟挖掘难负例的场景
        query = "What products does the tech company Apple make?"
        expected_contexts = ["doc_uuid_1"]
        
        hard_negatives = mine_hard_negatives(query, self.documents, expected_contexts, top_k=2)
        
        # 难负例必须排除真实目标文档（即高分但语义不相关的干扰文档）
        self.assertNotIn("doc_uuid_1", hard_negatives)
        # 最多返回我们指定的 top_k 数量
        self.assertLessEqual(len(hard_negatives), 2)
        
        # 预期：包含 apple 这个词，但并非目标文档的其它文档，将会被 BM25 赋予高分
        self.assertTrue(all(doc_id in ["doc_uuid_2", "doc_uuid_3", "doc_uuid_6"] for doc_id in hard_negatives))

if __name__ == '__main__':
    unittest.main()
