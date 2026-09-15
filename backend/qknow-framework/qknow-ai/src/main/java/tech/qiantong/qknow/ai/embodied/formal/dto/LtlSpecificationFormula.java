package tech.qiantong.qknow.ai.embodied.formal.dto;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 形式化线性时序逻辑 (LTL) 规范公式与极小乘积确定性有限状态自动机 (DFA) 契约 (Java 21 Record)
 * <p>
 * 封装规范唯一 ID、原始 LTL 规格表达式、原子命题字典映射、自动机总状态数、
 * 二维无锁查表转移矩阵 transitionTable[state][activePropsBitmask]、接受态集合、
 * 初始状态与编译时间戳。
 */
public record LtlSpecificationFormula(
        String specId,
        String rawLtlFormula,
        Map<String, Integer> atomicPropositions,
        int numStates,
        int[][] transitionTable,
        Set<Integer> acceptingStates,
        int initialState,
        long timestampMs
) {
    public static final int TRAP_STATE = -1; // 违规安全陷阱态标记

    public LtlSpecificationFormula {
        Objects.requireNonNull(specId, "specId 不能为空");
        Objects.requireNonNull(rawLtlFormula, "rawLtlFormula 不能为空");
        Objects.requireNonNull(atomicPropositions, "atomicPropositions 不能为空");
        Objects.requireNonNull(transitionTable, "transitionTable 不能为空");
        Objects.requireNonNull(acceptingStates, "acceptingStates 不能为空");
        if (numStates <= 0 || numStates > 64) {
            throw new IllegalArgumentException("极小化自动机状态数必须在 1 到 64 之间，当前为: " + numStates);
        }
        if (initialState < 0 || initialState >= numStates) {
            throw new IllegalArgumentException("初始状态超出合法状态区间: " + initialState);
        }
    }

    /**
     * 判断单步状态转移是否合法且推进至有效非陷阱状态
     */
    public boolean isValidTransition(int currentState, int activePropsBitmask) {
        if (currentState < 0 || currentState >= numStates) {
            return false;
        }
        if (activePropsBitmask < 0 || activePropsBitmask >= transitionTable[currentState].length) {
            return false;
        }
        int nextState = transitionTable[currentState][activePropsBitmask];
        return nextState != TRAP_STATE && nextState >= 0 && nextState < numStates;
    }

    /**
     * O(1) 二维查表获取下一 DFA 状态
     */
    public int getNextState(int currentState, int activePropsBitmask) {
        if (!isValidTransition(currentState, activePropsBitmask)) {
            return TRAP_STATE;
        }
        return transitionTable[currentState][activePropsBitmask];
    }

    /**
     * 判断指定状态是否为终态接受态 (Accepting State)
     */
    public boolean isAccepting(int state) {
        return acceptingStates.contains(state);
    }
}
