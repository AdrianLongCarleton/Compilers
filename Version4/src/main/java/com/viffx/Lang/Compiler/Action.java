package com.viffx.Lang.Compiler;

/**
 * Represents a parsing action in an LR parsing table.
 * <p>
 * An action consists of a type (e.g., SHIFT, REDUCE, GOTO, or ACCEPT) and
 * an integer value whose meaning depends on the action type:
 * <ul>
 *   <li>For {@link ActionType#SHIFT},  {@code data} is the target state to shift to.</li>
 *   <li>For {@link ActionType#GOTO},   {@code data} is the target state to transition to after a reduction.</li>
 *   <li>For {@link ActionType#REDUCE}, {@code data} is the production number to reduce by.</li>
 *   <li>For {@link ActionType#ACCEPT}, {@code data} is unused (often zero).</li>
 * </ul>
 *
 * @param type the kind of action to be performed (SHIFT, REDUCE, GOTO, ACCEPT)
 * @param data an integer value whose interpretation depends on {@code type}
 */
public record Action(ActionType type, int data) {}