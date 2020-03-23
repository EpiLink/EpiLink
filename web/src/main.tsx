import { h, render } from 'preact';

import '../styles/app.scss';

import { App } from './App';

// @ts-ignore
render(<App />, document.querySelector('#app'));
