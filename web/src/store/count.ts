import { Store }     from 'unistore';
import { LinkState } from './';

export function increment(store: Store<LinkState>) {
    setTimeout(() => {
        const state = store.getState();
        store.setState({
            count: state.count + 1
        });
    }, 1000);
}