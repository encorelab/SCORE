import { Component, OnInit } from '@angular/core';

import { StudentRun } from '../student-run';
import { StudentService } from '../student.service';

@Component({
  selector: 'app-student-run-list',
  templateUrl: './student-run-list.component.html',
  styleUrls: ['./student-run-list.component.scss']
})
export class StudentRunListComponent implements OnInit {

  runs: StudentRun[] = []; // array of StudentRun objects for current user
  filteredRuns: StudentRun[] = []; // filtered array of StudentRun objects to show in template
  search: string = ''; // search string for filtering run list
  // sort options for run list
  sortOptions: any[] = [
    { value: 'startTimeDesc', viewValue: 'Newest' },
    { value: 'startTimeAsc', viewValue: 'Oldest' },
    { value: 'projectNameAsc', viewValue: 'Project Name (A-Z)' },
    { value: 'projectNameDesc', viewValue: 'Project Name (Z-A)' }
  ];
  sortValue: string = 'startTimeDesc';
  loaded: boolean = false; // whether array of runs has been retrieved from server
  isAddingRun: boolean = false;
  registerRunRunCode: string = '';
  registerRunPeriods: string[] = [];
  selectedPeriod: string = '';

  constructor(private studentService: StudentService) { }

  ngOnInit() {
    this.getRuns();
  }

  getRuns() {
    this.studentService.getRuns()
      .subscribe(runs => {
        this.runs = runs;
        this.filteredRuns = runs;
        this.searchUpdated(this.search);
        this.sortUpdated(this.sortValue);
        this.loaded = true;
      });
  }

  searchUpdated(value: string) {
    this.search = value;
    this.filteredRuns = this.search ? this.performFilter(this.search) : this.runs;
  }

  sortUpdated(value: string) {
    this.sortValue = value;
    this.performSort(this.sortValue);
  }

  performFilter(filterValue: string) {
    filterValue = this.search.toLocaleLowerCase();
    // TODO: extract this for global use?
    return this.runs.filter((run: StudentRun) =>
      Object.keys(run).some(prop => {
        let value = run[prop];
        if (typeof value === 'undefined' || value === null) {
          return false;
        } else {
          return value.toString().toLocaleLowerCase().indexOf(filterValue) !== -1;
        }
      })
    );
  }

  performSort(sortValue: string) {
    switch(sortValue) {
      case 'startTimeDesc': {
        this.filteredRuns.sort( (a: StudentRun, b: StudentRun) => {
          return new Date(b.startTime).getTime() - new Date(a.startTime).getTime();
        });
        break;
      }
      case 'startTimeAsc': {
        this.filteredRuns.sort( (a: StudentRun, b :StudentRun) => {
          return new Date(a.startTime).getTime() - new Date(b.startTime).getTime();
        });
        break;
      }
      case 'projectNameAsc': {
        this.filteredRuns.sort( (a: StudentRun, b: StudentRun) => {
          const nameA = a.name.toLocaleLowerCase(); // ignore upper and lowercase
          const nameB = b.name.toLocaleLowerCase(); // ignore upper and lowercase
          if (nameA < nameB) {
            return -1;
          }
          if (nameA > nameB) {
            return 1;
          }
          // names must be equal
          return 0;
        });
        break;
      }
      case 'projectNameDesc': {
        this.filteredRuns.sort( (a: StudentRun, b: StudentRun) => {
          const nameA = a.name.toLocaleLowerCase(); // ignore upper and lowercase
          const nameB = b.name.toLocaleLowerCase(); // ignore upper and lowercase
          if (nameB < nameA) {
            return -1;
          }
          if (nameB > nameA) {
            return 1;
          }
          // names must be equal
          return 0;
        });
        break;
      }
    }
  }

  showAddRun() {
    this.isAddingRun = true;
  }

  addRun() {
    this.studentService.addRun(this.registerRunRunCode, this.selectedPeriod).subscribe((studentRun) => {
      if (studentRun.error) {
        alert(studentRun.error);
      } else {
        this.runs.unshift(studentRun);
        this.endAddRun();
      }
    });
  }

  endAddRun() {
    this.isAddingRun = false;
    this.clearPeriods();
  }

  cancelAddRun() {
    this.endAddRun();
  }

  clearPeriods() {
    this.selectedPeriod = '';
    this.registerRunPeriods = [];
  }

  checkRunCode(event: KeyboardEvent) {
    const runCode = (<HTMLInputElement>event.target).value;
    this.registerRunRunCode = runCode;
    if (this.isValidRunCodeSyntax(runCode)) {
      this.studentService.getRunInfo(runCode).subscribe(runInfo => {
        if (runInfo.error) {
          this.clearPeriods();
        } else {
          this.registerRunPeriods = runInfo.periods;
        }
      });
    } else {
      this.clearPeriods();
    }
  }

  isValidRunCodeSyntax(runCode: string) {
    return /^[a-zA-Z]*\d\d\d/.test(runCode);
  }
}