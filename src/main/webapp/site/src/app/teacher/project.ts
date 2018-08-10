import { Run } from "../domain/run";

export class Project {
  id: number;
  name: string;
  dateCreated: string;
  dateArchived: string;
  thumbIconPath: string;
  run: Run;
}