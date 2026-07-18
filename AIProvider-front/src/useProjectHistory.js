import { useCallback, useRef, useState } from "react";

const HISTORY_LIMIT = 80;

export function useProjectHistory(initialProject) {
  const [project, setState] = useState(initialProject);
  const currentRef = useRef(initialProject);
  const pastRef = useRef([]);
  const futureRef = useRef([]);
  const [, refresh] = useState(0);

  const replaceProject = useCallback((next) => {
    setState((current) => {
      const resolved = typeof next === "function" ? next(current) : next;
      currentRef.current = resolved;
      return resolved;
    });
  }, []);

  const commitProject = useCallback((next) => {
    setState((current) => {
      const resolved = typeof next === "function" ? next(current) : next;
      if (resolved === current) return current;
      pastRef.current = [...pastRef.current.slice(-(HISTORY_LIMIT - 1)), structuredClone(current)];
      futureRef.current = [];
      currentRef.current = resolved;
      return resolved;
    });
  }, []);

  const checkpoint = useCallback((before) => {
    if (!before || JSON.stringify(before) === JSON.stringify(currentRef.current)) return;
    pastRef.current = [...pastRef.current.slice(-(HISTORY_LIMIT - 1)), structuredClone(before)];
    futureRef.current = [];
    refresh((value) => value + 1);
  }, []);

  const undo = useCallback(() => {
    const previous = pastRef.current.pop();
    if (!previous) return false;
    futureRef.current.push(structuredClone(currentRef.current));
    currentRef.current = previous;
    setState(previous);
    return true;
  }, []);

  const redo = useCallback(() => {
    const next = futureRef.current.pop();
    if (!next) return false;
    pastRef.current.push(structuredClone(currentRef.current));
    currentRef.current = next;
    setState(next);
    return true;
  }, []);

  const resetHistory = useCallback((next) => {
    pastRef.current = [];
    futureRef.current = [];
    currentRef.current = next;
    setState(next);
  }, []);

  return {
    project,
    projectRef: currentRef,
    replaceProject,
    commitProject,
    checkpoint,
    undo,
    redo,
    resetHistory,
    canUndo: pastRef.current.length > 0,
    canRedo: futureRef.current.length > 0,
  };
}
