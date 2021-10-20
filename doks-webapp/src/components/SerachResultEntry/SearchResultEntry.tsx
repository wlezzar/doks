import { Card, Carousel, Space, Tag } from "antd";
import React from "react";
import { SearchResult } from "../../services/doks";
import Showdown from "showdown";
import styled from "styled-components";

const markdownConverter = new Showdown.Converter();

const CarouselWrapper = styled(Carousel)`
  > .slick-dots li button {
    background: #707070;
  }
  > .slick-dots li.slick-active button {
    background: #080808;
  }
`;

interface SearchResultEntryProps {
  entry: SearchResult;
}

const SearchResultEntry = (props: SearchResultEntryProps) => {
  console.log(props.entry);
  const infos = (
    <span>
      {Object.entries(props.entry.document.metadata).map(([key, value], i) => (
        <Tag key={i}>{`${key}: ${value}`}</Tag>
      ))}
      <Tag color="green">{props.entry.document.source}</Tag>
      <a href={props.entry.document.link}>Link</a>
    </span>
  );
  return (
    <div style={{ marginBottom: "1em" }}>
      <Card
        hoverable
        headStyle={{ backgroundColor: "#EEEEEE" }}
        size="small"
        title={props.entry.document.title}
        extra={infos}
      >
        <CarouselWrapper>
          {props.entry.matches["content"].map((m, i) => (
            <>
              <div
                style={{ marginBottom: "4rem" }}
                key={i}
                dangerouslySetInnerHTML={{
                  __html: markdownConverter.makeHtml(m),
                }}
              ></div>
            </>
          ))}
        </CarouselWrapper>
      </Card>
    </div>
  );
};

export default SearchResultEntry;
