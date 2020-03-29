import createStore, { Store } from 'unistore';

import { increment } from './count';

export class LinkState
{
    count: number;
    
    constructor()
    {
        this.count = 0;
    }
}

export const actions = (store: Store<LinkState>) => ({
    increment: (_: LinkState) => increment(store)
});

export default createStore(new LinkState());