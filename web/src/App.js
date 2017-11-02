import React, { Component, PropTypes } from 'react';
import { Router, Route } from 'react-router';
import { Provider } from 'react-redux';
import { store } from './redux';

export default class App extends Component {
  render() {
    return (
        <div>
          <Provider store={store}>
            <Router>
              <Route path='/' component={Welcome} />
            </Router>
          </Provider>
        </div>
    );
  }
};
