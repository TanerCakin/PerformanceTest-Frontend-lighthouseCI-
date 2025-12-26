# Authenticated Lighthouse Performance Testing – TestSuite

Java Test (TestNG)
|
|-- ProcessBuilder
|      |
|      |-- passes env vars
|      |-- runs Node script
|
Node.js (ws-lighthouse-auth.js)
|
|-- Puppeteer (login)
|-- Lighthouse (audit)
|-- writes HTML + JSON reports


## Overview

This project implements **authenticated Lighthouse performance testing** for TestSuite applications (e.g. `home`, `workhub`).

The solution combines:
- **Java** for test orchestration and CI integration
- **Node.js (Puppeteer + Lighthouse)** for authenticated browser control and performance auditing

This approach ensures accurate, stable, and CI-safe Lighthouse results for authenticated, Identity-protected applications.

---

## Why Lighthouse is used

- Lighthouse measures **real user–impact performance metrics** that functional tests cannot.
- It provides **Core Web Vitals** (LCP, CLS, TBT).
- It collects real browser performance traces using **Chrome DevTools Protocol (CDP)**.
- It enables **performance regression detection** in CI pipelines.
- It validates the **post-login user experience**, not just public pages.

> Selenium validates correctness.  
> Lighthouse validates user experience.

---

## Why Selenium Chrome cannot be reused

- **Selenium Chrome ≠ Lighthouse Chrome**
- Selenium controls Chrome via **WebDriver**, which is not sufficient for Lighthouse.
- Lighthouse requires **full CDP-level browser control** for:
    - tracing
    - throttling
    - storage resets
    - accurate metric calculation

Launching Chrome again is **mandatory**, not redundant.

---

## Why Puppeteer is required

Puppeteer is **not used for UI testing**.

It acts as a **Chrome DevTools controller** that:
1. Launches Chrome with CDP enabled
2. Handles Identity / OIDC authentication
3. Preserves authenticated browser state
4. Provides Lighthouse access to the same Chrome session

---

## Responsibility split

### Java
- Orchestrates when Lighthouse runs
- Selects the target URL or path
- Supplies configuration and credentials
- Integrates with CI and enforces pass/fail decisions

### Node.js (`lighthouse-auth.js`)
- Launches and controls Chrome via Puppeteer
- Detects Identity redirects
- Performs login (username → password → redirect)
- Runs Lighthouse using the authenticated session
- Generates HTML and JSON reports

---

## How authentication works

1. Puppeteer launches Chrome with DevTools enabled
2. Navigates to the target WorkSuite URL
3. Detects redirect to Identity provider
4. Submits username and password
5. Waits for redirect back to `*.work-suite.com`
6. Reuses the same browser session for Lighthouse

This prevents Lighthouse from auditing the login page and ensures the audit runs against the real application.

---

## Reports

- Reports are written to:  
  `FrontendPerformanceReports/`
- For each run:
    - `.report.html`
    - `.report.json`
- Reports include:
    - Performance
    - Accessibility
    - Best Practices
    - SEO

---

## Execution

### Prerequisites
- Node.js installed
- Lighthouse installed (`npm install -g lighthouse`)
- Puppeteer dependencies installed
- Chrome installed locally

