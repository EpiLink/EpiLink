import { h, render } from 'preact';
import { Provider }  from 'unistore/preact';

import '../styles/app.scss';

import { App } from './App';
import store   from './store';

render(
    <Provider store={store}>
        <App/>
    </Provider>,
    // @ts-ignore
    document.querySelector('#app')
);
