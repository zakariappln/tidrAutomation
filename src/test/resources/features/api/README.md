# API features

One folder per resource, one `.feature` per endpoint + method:
`features/api/<resource>/<action>.feature`.

Write them with the `api-gherkin-test-writer` agent (it asks its questions first), then make them
executable with `api-step-definition-writer`. The step vocabulary already implemented lives in
`src/test/java/com/ppln/automation/api/bdd/steps/` (RequestSteps, ResponseSteps, VariableSteps).
