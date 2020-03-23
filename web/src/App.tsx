import { h, Component } from 'preact';

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
                    Salut
                </div>

                <div id="footer">
                    <div id="left-footer">
                        <img id="logo" src="../assets/logo.svg" />
                        <span id="title">EpiLink</span>
                        <span id="version">v1.0.0</span>
                    </div>
                    <ul id="navigation">
                        <li class="navigation-item">Instance</li>
                        <li class="navigation-item">Confidentialité</li>
                        <li class="navigation-item">Sources</li>
                        <li class="navigation-item">À Propos</li>
                    </ul>
                </div>
            </div>
        );
    }
}