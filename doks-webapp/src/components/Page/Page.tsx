import React from 'react'
import classes from './Page.module.css'

export default (props: { title?: string, children: React.ReactNode }) => (
    <div className={classes.page}>
        {(props.title !== undefined) ? <h1>{props.title}</h1> : null}
        {props.children}
    </div>
)