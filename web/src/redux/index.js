import { createStore } from 'redux';
import reducer from './modules/reducer';

export const store = createStore(reducer);
