import { h } from 'preact';

import { LinkComponent } from '../LinkComponent';

export class Home extends LinkComponent
{
    constructor(props: Readonly<{}>)
    {
        super(props, []);
    }

    login()
    {
        alert('Not implemented');
    }

    renderStateful({ increment }: any): any
    {
        return (
            <div id="home">
                <img id="logo" src="../../assets/logo.svg" />
                <h1 id="title">Bienvenue</h1>

                <button id="discord" onClick={this.login}>
                    <img id="discord-logo" src="../../assets/discord.svg" />
                    <span id="discord-text" onClick={increment}>Se connecter via Discord</span>
                </button>
            </div>
        );
    }
}