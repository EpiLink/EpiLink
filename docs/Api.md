# Backend API

[Go back to main Documentation page](/docs/README.md)

This is documentation of the backend API of EpiLink.

This document reflects the API as it is implemented in the back-end, although it may not be fully accurate.

## Details

* All endpoints begin with `/api/v1`.
* ALL RETURNED OBJECTS ARE WRAPPED IN A ApiResponse OBJECT in the `data` field.
 
### ApiResponse

ALL API endpoints either return something of this form, or return no response at all.

```json5
{
  "success": true, // or false
  "message": "Hello", // nullable
  "data": {} // nullable, depends on the request
}
```

If `success` is false, then `data` is guaranteed to be a non-null [ErrorData](#errordata) object.

The message is nullable. If there is an error, and the message is null, check the ErrorData's description.

### ErrorData

```json5
{
  "code": 123,
  "description": "Broad description of the error"
}
``` 

#### Error codes

These are the different codes that can be seen in [ErrorData](#errordata) objects.

The description you see in the tables is very close to what you will receive in the ErrorData's description (there are
additional clarifications here).

More information can usually be found in the API response's message.

##### 1xx codes

These codes are specific for the registration process.

| Code | Description |
|:----:| ----------- |
| 100 | The registration request is missing some elements |
| 101 | Account creation is not allowed |
| 102 | Invalid authorization code (for `/register/authcode` endpoints) |
| 103 | This account does not have any attached email address |
| 104 | This account does not have any ID |
| 105 | This service is not known or does not exist (for `/register/authcode` endpoints) |


##### 2xx codes

These codes are for situations where an external API call failed.

| Code | Description |
|:----:| ----------- |
| 201 | Something went wrong with a Discord API call |
| 202 | Something went wrong with a Microsoft API call |


##### 3xx codes

These are general codes that can be encountered.

| Code | Description |
|:----:| ----------- |
| 300 | You need authentication to be able to access this resource |


##### 9xx codes

Special codes for when things really go wrong.

| Code | Description |
|:----:| ----------- |
| 999 | An unknown error occurred |


## Meta-information (/meta)

These endpoints can be used to retrieve information from the back-end that is used for operation on the front-end.

### Objects

#### InstanceInformation

```json5
{
  "title": "Title of the EpiLink instance",
  "logo": "https://url.to/instance/logo", // nullable
  "authorizeStub_msft": "...",
  "authorizeStub_discord": "..."
}
```

`authorizeStub` are OAuth2 authorization links (the ones you use for retrieving an authorization code) that are only missing a redirect URI. Append your own URI there. Don't forget to escape it for HTTP! (i.e. append `&redirect_uri=https%3A%2F%2Fmyexample.com%2F...` to the `authorizeStub` field).

### GET /meta/info

**Get basic information that can be used on the front-end.**

```http request
GET /api/v1/meta/info
```

Returns information about this instance, as a [InstanceInformation](#instanceinformation) JSON object.
 
## Registration (/register)

Registration state is maintained with a `RegisterSessionId` header, which you SHOULD include in all calls.

If you do not have any, (e.g. this is your first API request), you can call any API endpoint: the back-end will generate a session ID and give it back to you.

The OAuth2 design is like so:

* The API consumer (typically the EpiLink front-end) does the first part of the OAuth2 flow (that is, retrieving the access code). For this, the API can get the initial 
* The consumer then sends this ac

### Objects

#### RegistrationInformation

```json5
{
  "discordUsername": "example#1234", // nullable
  "discordAvatarUrl": "https://discordapi.example/myavatar.png" // nullable
  "email": "email@example.com", // nullable
}
```

#### RegistrationAuthCode

Used for sending an OAuth2 authentication code.

```http request
{
  "code": "...",
  "redirectUri": "..."
}
```

`redirectUri` is the exact URI that was used for the original authentication request that obtained the code.

#### RegistrationContinuation

Gives information on what to do next in the registration process, with an optional [RegistrationInformation](#registrationinformation) if the registration process should continue

```json5
{
  "next": "...", // either login or continue
  "attachment": { /* ... */ } // see below
}
```

The `attachment` is either:

* `null` if `next` is `login`
* A [RegistrationInformation](#registrationinformation) if `next` is `continue`

#### AdditionalRegistrationOptions

```json5
{
  "keepIdentity": true // or false 
}
```

### Get information - GET /register/info

**Retrieve information on the current registration process**

```http request
GET /api/v1/register/info
RegistrationSessionId: abcdef12345 # optional
```

Response: a [RegistrationInformation](#RegistrationInformation).

This is a simple way of getting access to the current state's information in case you need it. This can be used at any point during the registration process.

This can also be used if the registration process has not even started yet. This can be useful to get a session ID directly.

### DELETE /register

**Cancel the registration process**

```http request
DELETE /api/v1/register
RegistrationSessionId: abcdef12345 # mandatory
``` 

Aborts the registration attempt and clears all session information on the back-end.

### POST /register/authcode/service

**Continue the register process with the given authcode for the user.**

Where service can be `msft` or `discord`

```http request
POST /api/v1/register/authcode/service
RegistrationSessionId: abcdef12345 # optional
Content-Type: application/json # mandatory
```

The request content is a JSON [RegistrationAuthCode](#registrationauthcode)

Response: a [RegistrationContinuation](#registrationcontinuation).

**Be careful with the response!** There are four possible scenarios:

* HTTP error, no body: There was an error processing the request, usually internal 
* HTTP error with API response body (success set to false and message non-null): You can display the message to the user safely. It may, for example, tell the user that he is banned.
* The continuation's `next` is set to `continue`: You can go on with the registration requests.
* The continuation's `next` is set to `login`: The registration is no longer valid, and the user has been logged in. The response has SessionId header that you can use right away. 

### POST /register

**Complete signup process.**

```http request
POST /api/v1/register
RegistrationSessionId: abcdef12345 # mandatory
Content-Type: application/json # mandatory
```

Complete the registration request.

Content is a [AdditionalRegistrationOptions](#additionalregistrationoptions) JSON object.

Response: No data attachment in the usual ApiResponse.

* If the request was unsuccessful, the message contains more information
* If the request was successful, the user has been logged in and the response contains a SessionId header that can be used to access user-related resources.

## Connected user information /user

All endpoints under `/user` expect the `SessionId` header to be set.

### GET /user

(Temporary) Returns some text on the logged in user.