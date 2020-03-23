import { h, Component } from 'preact';

export class Home extends Component
{
    constructor(props: Readonly<{}>)
    {
        super(props);
    }

    login()
    {
        alert('Not implemented');
    }

    render()
    {
        return (
            <div id="home">
                <img id="logo" src="../../assets/logo.svg" />
                <h1 id="title">Bienvenue</h1>

                <button id="discord" onClick={this.login}>
                    <img id="discord-logo" src="../../assets/discord.svg" />
                    <span id="discord-text">Se connecter via Discord</span>
                </button>
            </div>
        );
    }
}