import { h, Component } from 'preact';
import Router from 'preact-router';

import { Home } from './views/Home';
import { NotFound } from './views/NotFound';

const ROUTES = [
    { title: 'Instance', path: '/instance' },
    { title: 'Confidentialité', path: '/privacy' },
    { title: 'Sources', path: 'https://github.com/Litarvan/EpiLink' }, // TODO: Dynamic
    { title: 'À Propos', path: '/about' }
];

export class App extends Component
{
    constructor(props: Readonly<{}>)
    {
        super(props);
    }

    render()
    {
        return (
            <div id="layout">
                <div id="content">
                    <Router>
                        <Home path="/" />
                        <NotFound default />
                    </Router>
                </div>

                <div id="footer">
                    <div id="left-footer">
                        <a id="home-button" href="/">
                            <img id="logo" src="../assets/logo.svg" />
                            <span id="title">EpiLink</span>
                        </a>
                        <span id="version">v1.0.0</span>
                    </div>
                    <ul id="navigation">
                        {ROUTES.map(route =>
                            <li class="navigation-item">
                                <a href={route.path}>{route.title}</a>
                            </li>
                        )}
                    </ul>
                </div>
            </div>
        );
    }
}