import { useLayoutEffect, useRef, DependencyList, ReactNode } from 'react';
import { useTopbar } from '@/context/TopbarContext';

export function useTopbarSlot(content: ReactNode, deps: DependencyList = []) {
    const { setTopbarContent } = useTopbar();
    const contentRef = useRef<ReactNode>(null);
    const setTopbarContentRef = useRef(setTopbarContent);
    
    contentRef.current = content;
    setTopbarContentRef.current = setTopbarContent;

    useLayoutEffect(() => {
        setTopbarContentRef.current(contentRef.current);
    }, deps);
}