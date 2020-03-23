import { h, Component } from 'preact';

export class NotFound extends Component
{
    constructor(props: Readonly<{}>)
    {
        super(props);
    }

    render()
    {
        return (
            <div id="not-found">
                <h1 id="title">404</h1>
                <span id="description">La page demandée n'existe pas</span>
            </div>
        )
    }
}