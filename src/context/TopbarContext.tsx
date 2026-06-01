import React, {
    createContext, useContext, useRef,
    useSyncExternalStore, useCallback, ReactNode
} from 'react';

type Listener = () => void;

interface TopbarStore {
    getContent: () => ReactNode;
    setContent: (content: ReactNode) => void;
    subscribe: (listener: Listener) => () => void;
}

function createTopbarStore(): TopbarStore {
    let content: ReactNode = null;
    const listeners = new Set<Listener>();

    return {
        getContent: () => content,
        setContent: (next) => {
            content = next;
            listeners.forEach(l => l());
        },
        subscribe: (listener) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        },
    };
}

const TopbarStoreContext = createContext<TopbarStore | null>(null);

export const TopbarProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    const storeRef = useRef<TopbarStore>(createTopbarStore());

    return (
        <TopbarStoreContext.Provider value={storeRef.current}>
            {children}
        </TopbarStoreContext.Provider>
    );
};

// Used by AppLayout to DISPLAY the topbar content
export const useTopbarDisplay = () => {
    const store = useContext(TopbarStoreContext);
    if (!store) throw new Error('useTopbarDisplay must be inside TopbarProvider');

    const topbarContent = useSyncExternalStore(
        store.subscribe,
        store.getContent,
        () => null
    );

    return { topbarContent };
};

// Used by pages to SET the topbar content
export const useTopbar = () => {
    const store = useContext(TopbarStoreContext);
    if (!store) throw new Error('useTopbar must be inside TopbarProvider');

    const setTopbarContent = useCallback((content: ReactNode) => {
        store.setContent(content);
    }, [store]);

    return { setTopbarContent };
};