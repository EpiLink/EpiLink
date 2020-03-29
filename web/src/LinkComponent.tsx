import { Component, h } from 'preact';
import { connect }      from 'unistore/preact';

import { actions } from './store';

export abstract class LinkComponent extends Component
{
    mapState: Array<string>;
    
    constructor(props: Readonly<{}>, mapState: Array<string>)
    {
        super(props);
        this.mapState = mapState;
    }
    
    render()
    {
        const Content = connect(this.mapState, actions)(this.renderStateful);
        return <Content/>;
    }
    
    abstract renderStateful(args: any): any;
}