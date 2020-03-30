import { Store }   from 'unistore';
import { request } from '../api';

import { LinkState } from './index';

export interface LinkMeta
{
    title: string;
    logo: string;
    authorizeStub_msft: string;
    authorizeStub_discord: string;
}

export async function fetchMeta(store: Store<LinkState>)
{
    const state = store.getState();
    if (state.meta) {
        return;
    }
    
    const meta = await request<LinkMeta>('/meta');
    store.setState({ meta });
}