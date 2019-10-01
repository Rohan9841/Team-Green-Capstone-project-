import { Optional } from '@angular/core';
import { Calendar } from './calendar-list/calendar.model';
import { DateRange } from '../appointment/appointment-model/date-range.model';

export class CalEvent{
    constructor(
        public title:string,
        public description: string,
        public location: string,
        public email: string[],
        public dateRange: DateRange[]
    ){}
}