import React from 'react';

export class App extends React.Component
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