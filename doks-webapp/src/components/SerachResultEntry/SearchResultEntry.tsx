import { Card, Tag } from "antd"
import React from "react"
import { SearchResult } from "../../services/doks"

interface SearchResultEntryProps {
    entry: SearchResult
}

const SearchResultEntry = (props: SearchResultEntryProps) => {
    console.log(props.entry)
    const infos = (
        <span>
            {
                Object.entries(props.entry.document.metadata).map(([key, value], i) => <Tag key={i}>{`${key}: ${value}`}</Tag>)
            }
            <Tag color="green">{props.entry.document.source}</Tag>
            <a href={props.entry.document.link}>Link</a>
        </span>
    )
    return (
        <div style={{ marginBottom: "1em" }}>
            <Card hoverable headStyle={{backgroundColor: "#EEEEEE"}} size="small" title={props.entry.document.title} extra={infos}>
                <ul>
                    {props.entry.matches["content"].map((m, i) => <li key={i} dangerouslySetInnerHTML={{ __html: m }}></li>)}
                </ul>
            </Card>
        </div>
    )
}

export default SearchResultEntry