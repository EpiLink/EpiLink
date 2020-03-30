import createStore, { Store } from 'unistore';

import { fetchMeta, LinkMeta } from './meta';

export class LinkState
{
    meta: LinkMeta | null;
    
    constructor()
    {
        this.meta = null;
    }
}

export const actions = (store: Store<LinkState>) => ({
    fetchMeta: (_: LinkState) => fetchMeta(store)
});

export default createStore(new LinkState());