import { Input } from 'antd'
import { debounce } from "lodash"
import React, { ChangeEvent, useCallback, useState } from "react"
import Async from "../../hoc/Async/Async"
import { Doks, SearchResult } from "../../services/doks"
import Page from "../Page/Page"
import SearchResultEntry from "../SerachResultEntry/SearchResultEntry"

const SearchPage = () => {

    // let doks = new Doks(window.location.origin)
    let doks = new Doks("http://localhost:8888")

    let [query, setQuery] = useState('')
    let [results, setResults] = useState<SearchResult[]>([])
    let [loading, setLoading] = useState<boolean>(false)
    let [error, setError] = useState<string>()

    let debouncedSearch = useCallback(
        debounce(
            async (query: string) => {
                setLoading(true)
                try {
                    let results = await doks.search(query)
                    setError(undefined)
                    setResults(results)
                } catch (err) {
                    setError(`${err}`)
                    setResults([])
                } finally {
                    setLoading(false)
                }
            },
            500
        ),
        []
    )

    const handleChange = async (event: ChangeEvent<HTMLInputElement>) => {
        const { value: nextValue } = event.target
        setQuery(nextValue)
        debouncedSearch(nextValue)
    }

    return (
        <Page title="DOKS">
            <h2>Search</h2>
            <Input placeholder="e.g Kafka AWS" value={query} onChange={handleChange} style={{ marginBottom: "1em" }} />
            <Async isLoading={loading} error={error}>
                {results.map(r => <SearchResultEntry key={r.document.id} entry={r} />)}
            </Async>
        </Page>
    )
}

export default SearchPage