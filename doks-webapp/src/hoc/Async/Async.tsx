import React from 'react'
import classes from './Async.module.css'
import { Spin } from 'antd'

interface AsyncProps {
    isLoading: boolean
    error?: string
    children: any
}

const Async = (props: AsyncProps) => {
    if (props.isLoading) return (
        <div className={classes.async}>
            <Spin size="large" />
        </div>
    )
    else if (props.error) return (
        <div>
            <h3>Something wrong happened !</h3>
            <p>error : {`${props.error}`}</p>
        </div>
    )
    else {
        return props.children
    }
}

export default Async