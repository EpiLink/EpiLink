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
            <div>
                <h1>Hello world !</h1>
            </div>
        );
    }
}