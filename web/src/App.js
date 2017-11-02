import React, { Component, PropTypes } from 'react';
import { Router, Route } from 'react-router';
import { Provider } from 'react-redux';
import { store } from './redux';
import Welcome from './views/Welcome';

export default class App extends Component {
  render() {
    console.log(browserHistory);
    return (
        <div>
          <Provider store={store}>
            <Router history={browserHistory}>
              <Route path='/' component={Welcome} />
            </Router>
          </Provider>
        </div>
    );
  }
};
