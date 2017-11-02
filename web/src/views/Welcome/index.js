import React, { Component, Proptypes } from 'react';
import { connect } from 'react-redux';

export default class Welcome extends Component {

	render() {
		const { isProfileSetUp } = this.props;

		return (
			"Hello React"
		);
	}
}
