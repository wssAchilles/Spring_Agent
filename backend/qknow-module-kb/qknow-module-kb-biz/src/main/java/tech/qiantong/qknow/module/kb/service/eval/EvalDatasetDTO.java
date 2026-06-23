package tech.qiantong.qknow.module.kb.service.eval;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tech.qiantong.qknow.hermes.eval.EvaluationDataset.EvalItem;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EvalDatasetDTO {

    private Long id;
    private String name;
    private Long knowledgeBaseId;
    private List<EvalItem> items = new ArrayList<>();
}
