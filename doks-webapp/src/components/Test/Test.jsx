import Showdown from "showdown"

export const TestComponent = () => {
	const md = `
# Hello world
I'm here
`

	const converter = new Showdown.Converter()

	const html = converter.makeHtml(md)

	return (
		<div dangerouslySetInnerHTML={{"__html": html}}></div>
	)
} 
