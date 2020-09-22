import React from 'react';
import './App.css';
import SearchPage from './components/SearchPage/SearchPage';
import { Layout } from 'antd';

function App() {

  return (
    <div className="App">
      <Layout>
        <Layout.Header className="header"><img className="logo" src="/logo.png" /></Layout.Header>
        <Layout.Content style={{backgroundColor: "white"}}>
          <SearchPage />
        </Layout.Content>
      </Layout>
    </div>
  );
}



export default App;
