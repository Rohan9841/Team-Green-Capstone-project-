import { Component, OnInit } from "@angular/core";
import { BreakpointObserver, Breakpoints } from "@angular/cdk/layout";
import { TokenStorageService } from "src/app/auth/token-storage.service";
import { Router } from "@angular/router";
// import { Observable } from "rxjs";
// import { map, shareReplay } from "rxjs/operators";

@Component({
  selector: "app-vertical-navigation",
  templateUrl: "./vertical-navigation.component.html",
  styleUrls: ["./vertical-navigation.component.css"]
})
export class VerticalNavigationComponent implements OnInit {
  // isHandset$: Observable<boolean> = this.breakpointObserver
  //   .observe(Breakpoints.Web)
  //   .pipe(
  //     map(result => result.matches),
  //     shareReplay()
  //   );

  // constructor(private breakpointObserver: BreakpointObserver) {}
  constructor(
    private tokenStorageService: TokenStorageService,
    private router: Router
  ) {}

  ngOnInit() {}
  logout() {
    this.tokenStorageService.signOut();
    this.router.navigate(["/login"]);
  }
}
