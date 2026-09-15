package tech.qiantong.qknow.ai.policy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 双重稳健离线策略评估器 (Doubly Robust OPE, 定理 1.1)
 */
@Component
public class DoublyRobustOpeEvaluator {

    private static final Logger log = LoggerFactory.getLogger(DoublyRobustOpeEvaluator.class);

    // 重要性权重绝对截断上限 (消除分母趋零引发的方差爆炸)
    public static final double MAX_IMPORTANCE_WEIGHT = 10.0;

    /**
     * 轨迹单步记录
     */
    public record TrajectoryStep(
            String state,
            String action,
            double reward,
            String nextState,
            double behaviorProb,
            Map<String, Double> costs
    ) {}

    /**
     * 策略接口
     */
    public interface Policy {
        double getActionProb(String state, String action);
        List<String> getAvailableActions(String state);
    }

    /**
     * 基准价值回归模型接口
     */
    public interface BaselineValueModel {
        double predictQ(String state, String action);
        double predictV(String state);
    }

    /**
     * 评估结果
     */
    public record OpeEvaluationResult(
            double doublyRobustValue,
            double directMethodValue,
            double importanceSamplingValue,
            double doublyRobustVariance,
            double importanceSamplingVariance
    ) {}

    /**
     * 执行双重稳健离线策略评估 (定理 1.1)
     */
    public OpeEvaluationResult evaluatePolicy(
            List<TrajectoryStep> trajectory,
            Policy targetPolicy,
            BaselineValueModel baselineModel
    ) {
        if (trajectory == null || trajectory.isEmpty()) {
            throw new IllegalArgumentException("评估轨迹集不能为空");
        }
        if (targetPolicy == null || baselineModel == null) {
            throw new IllegalArgumentException("目标策略与基准模型均不能为空");
        }

        int n = trajectory.size();
        double sumDr = 0.0;
        double sumDm = 0.0;
        double sumIs = 0.0;

        double[] drSteps = new double[n];
        double[] isSteps = new double[n];

        double discount = 0.95;

        for (int i = 0; i < n; i++) {
            TrajectoryStep step = trajectory.get(i);
            double piProb = targetPolicy.getActionProb(step.state(), step.action());
            double muProb = Math.max(0.001, step.behaviorProb()); // 行为概率下界保护

            // 原始重要性权重与裁剪重要性权重
            double rawRho = piProb / muProb;
            double clippedRho = Math.min(rawRho, MAX_IMPORTANCE_WEIGHT);

            // 1. 直接模型基线价值 V_hat(s) = sum_a pi(a|s) Q_hat(s, a)
            double vHatState = 0.0;
            List<String> actions = targetPolicy.getAvailableActions(step.state());
            for (String a : actions) {
                vHatState += targetPolicy.getActionProb(step.state(), a) * baselineModel.predictQ(step.state(), a);
            }

            // 下一状态基线价值
            double vHatNextState = (step.nextState() != null) ? baselineModel.predictV(step.nextState()) : 0.0;
            double qHatCurrent = baselineModel.predictQ(step.state(), step.action());

            // 2. 双重稳健单步估计量 (定理 1.1)
            // DR_step = V_hat(s) + rho_clipped * (reward + gamma * V_hat(s') - Q_hat(s, a))
            double drStep = vHatState + clippedRho * (step.reward() + discount * vHatNextState - qHatCurrent);

            // 3. 普通重要性采样单步估计量 (纯 IS)
            double isStep = rawRho * step.reward();

            drSteps[i] = drStep;
            isSteps[i] = isStep;

            sumDr += drStep;
            sumDm += vHatState;
            sumIs += isStep;
        }

        double meanDr = sumDr / n;
        double meanDm = sumDm / n;
        double meanIs = sumIs / n;

        // 计算经验样本方差
        double varDr = 0.0;
        double varIs = 0.0;
        for (int i = 0; i < n; i++) {
            varDr += Math.pow(drSteps[i] - meanDr, 2);
            varIs += Math.pow(isSteps[i] - meanIs, 2);
        }
        varDr = varDr / n;
        varIs = varIs / n;

        log.info("DR-OPE 评估完成: meanDR={}. meanIS={}, varDR={}, varIS={}",
                String.format("%.4f", meanDr), String.format("%.4f", meanIs),
                String.format("%.4f", varDr), String.format("%.4f", varIs));

        return new OpeEvaluationResult(meanDr, meanDm, meanIs, varDr, varIs);
    }
}
