import { useLayoutEffect, useRef, DependencyList, ReactNode } from 'react';
import { useTopbar } from '../context/TopbarContext';

export function useTopbarSlot(content: ReactNode, deps: DependencyList = []) {
    const { setTopbarContent } = useTopbar();
    const contentRef = useRef<ReactNode>(null);
    contentRef.current = content;

    useLayoutEffect(() => {
        setTopbarContent(contentRef.current);
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, deps);
}