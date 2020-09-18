
export class Doks {

    constructor(private baseUrl: string) { }

    async search(query: string): Promise<SearchResult[]> {
        if (query === '') {
            return []
        }

        let url = `${this.baseUrl}/api/search?q=${encodeURIComponent(query)}`
        console.log(url)
        let response = await fetch(url)
        let body = await response.text()

        if (!response.ok) {
            throw new Error(`http response error: ${response.status} (body: ${body})`);
        }

        return JSON.parse(body) as SearchResult[]
    }
}

export interface SearchResult {
    document: Doc
    score: number
    matches: { [key: string]: string[] }
}

export interface Doc {
    id: string
    title: string
    link: string
    source: string
    metadata: { [key: string]: string }
}