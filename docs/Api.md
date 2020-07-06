# Back-end API

**The API is not intended to be used as an external API**, although it technically could be used as such. Instance maintainers may forbid (e.g. through their ToS) usage of the API by anything other than the front-end.

This is documentation of the backend API of EpiLink.

This document reflects the API as it is implemented in the back-end, although it may not be fully accurate.

## Rate limiting

EpiLink follows [Discord's way of rate limiting](https://discordapp.com/developers/docs/topics/rate-limits), except that the `Retry-After` header and `retry_after` JSON values are *always* an integer number of seconds. All endpoints are rate-limited, the exact rates depending on the endpoint.

The rate limiting is based on three factors (keys):

* The Remote IP address (caller key)
* The route (route key)
* Major parameter (additional key)

Note that 429 errors *never* return API responses, they always return a JSON object with three values, `message`, `retry_after` (in seconds, not milliseconds like Discord) and `global`.

## Details

* All endpoints begin with `/api/v1`.
* ALL RETURNED OBJECTS ARE WRAPPED IN A ApiResponse OBJECT in the `data` field.
 
### ApiResponse

**ALL** API endpoints either return something of this form, or return no response at all. Some meta endpoints return raw HTML directly. All exceptions to this rule are noted.

```json5
{
  "success": true, // or false
  "message": "Hello", // nullable
  "data": {} // nullable, depends on the request
}
```

* If `success` is false, then `data` is guaranteed to be a non-null [ErrorData](#errordata) object, and `message` is not null.
* If `success` is true, then `message` may be null and `data` may be null.

### ErrorData

```json5
{
  "code": 123,
  "description": "Broad description of the error"
}
``` 

Provides information on the error that happened. A list of codes is available below. The description is always the same,
see the api response's message for more specific information about the error.

## Error codes

These are the different codes that can be seen in [ErrorData](#errordata) objects.

The description you see in the tables is very close to what you will receive in the ErrorData's description (there are
additional clarifications here).

More information can usually be found in the [ApiResponse](#apiresponse)'s message.

### 1xx codes

These codes are specific for the registration and identity modification processes.

| Code | Description |
|:----:| ----------- |
| 100 | The registration request is missing some elements |
| 101 | Account creation is not allowed |
| 102 | Invalid authorization code (for `/register/authcode` and `/user/identity` endpoints) |
| 103 | This account does not have any attached email address |
| 104 | This account does not have any ID |
| 105 | This service is not known or does not exist (for `/register/authcode` endpoints) |
| 110 | This account already has its identity recorded in the database (for the `/user/identity` endpoint) |
| 111 | This account's identity cannot be removed, because it is not present in the database (for the `/user/identity` endpoint) |
| 112 | This account's identity does not match the one retrieved via the authcode (different IDs). |

### 2xx codes

These codes are for situations where an external API call failed.

| Code | Description |
|:----:| ----------- |
| 201 | Something went wrong with a Discord API call |
| 202 | Something went wrong with a Microsoft API call |


### 3xx codes

These are general codes that can be encountered.

| Code | Description |
|:----:| ----------- |
| 300 | You need authentication to be able to access this resource |
| 301 | You do not have the permission to do that (i.e. you are logged in but can't do that) |

### 4xx codes

These are codes that can be encountered in the administration APIs only. (These are not HTTP errors!)

| Code | Description |
|:----:| ----------- |
| 400 | Invalid admin request |
| 401 | Incomplete admin request |
| 402 | Invalid request: (target) user does not exist |
| 403 | Invalid or incoherent ID |
| 404 | Invalid Instant (date+hour) format |
| 430 | Attempted to get the identity of a non-identifiable user |

### 9xx codes

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
  "authorizeStub_discord": "...",
  "idPrompt": "...",
  "footerUrls": [ /* ... */ ] ,
  "contacts": [ /* ... */ ]
}
```

The `authorizeStub` values are OAuth2 authorization links (the ones you use for retrieving an authorization code) that are only missing a redirect URI. Append your own URI there. Don't forget to escape it for HTTP! (i.e. append `&redirect_uri=https%3A%2F%2Fmyexample.com%2F...` to the `authorizeStub` field).

`idPrompt` is the text that should be shown below the "I want EpiLink to remember my identity" checkbox. It is inline HTML that is meant to be embedded within a web page.

`footerUrls` is a list of [FooterUrl](#footerurl) objects, each describing a link that should be displayed in the footer. These links are customized by the back-end.

`contacts` is a list of [ContactInformation](#contactinformation) objects, each describing a person that can be contacted (e.g. an instance maintainer). May be empty. *(since version 0.2.0)*

#### FooterUrl

```json5
{
  "name": "URL Title",
  "url": "https://..."
}
```

#### ContactInformation

?> Since version 0.2.0

```json5
{
  "name": "Mike Schmidt",
  "email": "mike.schmidt@freddyfazbear.pizza"
}
```

### GET /meta/info

**Get basic information that can be used on the front-end.**

```http request
GET /api/v1/meta/info
```

Returns information about this instance, as a [InstanceInformation](#instanceinformation) JSON object.
 
### GET /meta/tos

**Get the terms of services.**

```http request
GET /api/v1/meta/tos
```

> **DOES NOT RETURN AN API RESPONSE.** This endpoint returns inline HTML or a PDF file directly. `Content-Type: text/html` or `Content-Type: application/pdf`

Returns the terms of services of this instance, as inline HTML or as a PDF.

Example:
```html
<p>My terms of services are the best terms of services!</p>
```

(or a PDF file's contents)

### GET /meta/privacy

**Get the privacy policy.**

```http request
GET /api/v1/meta/privacy
```

> **DOES NOT RETURN AN API RESPONSE.** This endpoint returns inline HTML directly or a PDF file. `Content-Type: text/html` or `Content-Type: application/pdf`

Returns the privacy policy of this instance, as inline HTML or as a PDF.

Example:
```html
<p>My privacy policy is very private!</p>
```

(or a PDF file's contents)

## Registration (/register)

Registration state is maintained with a `RegisterSessionId` header, which you SHOULD include in all calls.

If you do not have any, (e.g. this is your first API request), you can call any registration API endpoint: the back-end will generate a session ID and give it back to you.

The OAuth2 design is like so:

* The API consumer (typically the EpiLink front-end) does the first part of the OAuth2 flow (that is, retrieving the access code). For this, the API can get a stub of the authorization URL using [`/meta/info`](#get-metainfo). You only need to add a `redirect_uri` there.
* The consumer then sends this access code with the [`POST /register/authcode/<service>`](#post-registerauthcodeservice)
  endpoints.
  
### Registration + Log in flow

The registration process roughly looks like this:

* Get a `SessionRegisterId`, which you can get from any endpoint. We recommend using [the `/register/info`](#get-information---get-registerinfo) endpoint
* Provide a Microsoft or Discord authorization code using [the `/register/authcode/service`](#post-registerauthcodeservice) endpoints
* Provide the other authorization code using the other authorization code endpoint. (e.g. if you started with Discord, provide the Microsoft code next)
* Complete the registration by calling [the `/register` endpoint](#post-register) with whether or not the user wants to have their identity kept in the system. The account has been created, congrats!

At any point, a registration session can be cancelled using the [DELETE `/register`](#delete-register) endpoint.

EpiLink has **no dedicated log in flow.** We recommend that you simply use the registration flow for everything and first start with the Discord authorization code. Once that code has been obtained, you can just check whether the back-end asks you to continue with the registration procedure or has logged the user in.

### Objects

#### RegistrationInformation

This object provides information on the current registration process' status and information. It can be obtained by calling [`GET /register/info`](#get-information---get-registerinfo) and can be found in the responses of most registration endpoints.

```json5
{
  "discordUsername": "example#1234", // nullable
  "discordAvatarUrl": "https://discordapi.example/myavatar.png", // nullable
  "email": "email@example.com" // nullable
}
```

Each field represents some information about the current process.
 
* `discordUsername` is the Discord username associated with the current registration process, or null if no Discord account is recorded in the current registration process. 
* `discordAvatarUrl` is a URL to the avatar of the Discord user. This may be null if the user does not have an avatar, or if no Discord account is recorded in the current registration process.
* `email` is the user's email address (as provided by the Microsoft Graph API), or null if no Microsoft account is recorded in the current registration process.

#### RegistrationAuthCode

Used for sending an OAuth2 authentication code.

```json5
{
  "code": "...",
  "redirectUri": "..."
}
```

`redirectUri` is the exact `redirect_uri` URI that was used for the original authentication request that obtained the code. This is required for security reasons, although no redirection to this address actually happens.

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
* HTTP error with API response body (success set to false and message non-null): You can display the message to the user safely. It may, for example, tell the user that he is banned. The [error code](#error-codes) in the [attached data](#errordata) will tell you more about which error happened. [Error code 101](#1xx-codes) (Account creation is not allowed) is very relevant here.
* The continuation's `next` is set to `continue`: You can go on with the registration requests.
* The continuation's `next` is set to `login`: The registration is no longer valid, and the user has been logged in. The response has a SessionId header that you can use right away. 

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

### Objects

#### UserInformation

Contains information about the currently logged in user.

```json5
{
    "discordId": "...",
    "username": "...",
    "avatarUrl": "...", // nullable
    "identifiable": true // or false
}
```

Where:

* `discordId` is the Discord ID of the user
* `username` is the Discord username of the user (should be displayed as the normal username in the interface). For example `My awesome name#1234`
* `avatarUrl` (may be null) is a URL to the Discord avatar of the user, or null if Discord did not reply with any URL.
* `identifiable` is a boolean. If true, the user has their identity recorded in the database. If false, the user does not have their identity in the database. 

#### IdAccessLogs

Contains information about all of the ID Accesses of a user.

```json5
{
    "manualAuthorsDisclosed": true, // or false
    "accesses": [
        // IdAccess objects    
    ]
}
```

* `manualAuthorsDisclosed`: This value is only intended for displaying a message to the user telling him that it is normal that they don't see the name of the author when the request is manual. True if such a message should be displayed, false otherwise. This does NOT determine whether the author will actually be available or not.
* `accesses`: List of [IdAccess](#idaccess) objects

#### IdAccess

Represents a single ID access.

```json5
{
    "automated": true, // or false
    "author": "...", // nullable
    "reason": "...",
    "timestamp": "...", // ISO-8601 Instant format, e.g. 2011-12-03T10:15:30Z, this is in UTC
}
```

* `automated`: True if the request was conducted by a bot, false otherwise
* `author`: The name of the author. No particular format is guaranteed, but this name should be enough for a human to distinguish who conducted the request. Null if the author is not available to the user -- while the author is always logged on the back-end, a policy can be set to prevent users from accessing the identity of the requester in specific cases.
* `reason`: The human-readable reason for the access. Again, no particular format is guaranteed.
* `timestamp`: A ISO-8601 Instant of when the request happened, always in UTC (the `Z` at the end).

### GET /user

**Get information on the currently logged in user.**

```http request
GET /api/v1/user
SessionId: abcdef123456 # mandatory
```

Returns a [UserInformation](#userinformation) object about the currently logged in user.

### POST /user/logout

**Logs the user out and deletes session information on the back-end.**

```http request
POST /api/v1/user/logout
SessionId: abcdef123456 # mandatory
```

Replies with a regular, no data successful [ApiResponse](#apiresponse).

### GET /user/idaccesslogs

**Get all identity accesses for the currently logged in user.**

```http request
GET /api/v1/user/idaccesslogs
```

Returns an [IdAccessLogs](#idaccesslogs) object about the currently logged in user.

### POST /user/identity

**Add a Microsoft ID to the account if the identity was previously not registered**

```http request
POST /api/v1/user/identity
SessionId: abcdef1234 # mandatory
Content-Type: application/json # mandatory
```

The request content is a JSON [RegistrationAuthCode](#registrationauthcode).

This endpoint uses the provided Microsoft authorization code to record the e-mail address of the account in the database. Note that the Microsoft account retrieved via the authorization code must be the one that was used for creating the account in the first place -- otherwise, the operation fails with [a 112 error code](#error-codes).

Returns a classic success [API response](#apiresponse) if successful (with HTTP Code 200), or an API error otherwise.

Error codes 102 (wrong authcode), 110 (identity already kept), and 112 (identity does not match) are particularly relevant here. [See all error codes](#error-codes).

Note that the back-end will always consume the authorization code, although it will discard the retrieved token immediately in case of an error (e.g. already linked).

Upon success, triggers a role update.

### DELETE /user/identity

**Removes a user's identity from the database**

```http request
DELETE /api/v1/user/identity
SessionId: abcdef1234 # mandatory
```

This request has no body.

Returns a classic success [API response](#apiresponse) if successful (with HTTP Code 200), or an API error otherwise.

Error code 111 (identity already unknown) is relevant here. [See all error codes](#error-codes).

Upon success, triggers a role update.

## Administrative endpoints /admin

?> Since version 0.3.0

All endpoints are checked: the caller must have admins permissions (by specifying the caller's Discord ID as an "admin") *and* be identifiable.

### Objects

#### IdRequest

```json5
{
  "target": "...", // Discord ID of the targeted user
  "reason": "..." // Reason that will be notified to the user
}
```

#### IdRequestResult

```json5
{
  "target": "...", // Discord ID of the user
  "identity": "..." // Identity of the user
}
```

#### RegisteredUserInformation

```json5
{
  "discordId": "...",
  "msftIdHash": "...", 
  "created": "...",
  "identifiable": true
}
```

- `discordId`: The Discord ID of the user (which you most probably already know)
- `msftIdHash`: The Microsoft ID hash of the user (URL-safe Base64)
- `created`: A ISO-8601 Instant of when the account was created, always in UTC (the `Z` at the end).
- `identifiable`: True or false, whether the user can be identified through an ID access or not.

#### UserBans

```json5
{
  "banned": true, // or false, tells whether any ban is currently active
  "bans": [ // Possibly empty array of BanInfo objects
    //...
  ]
}
```

#### BanInfo

```json5
{
  "id": 0,
  "revoked": false, // or true,
  "author": "...",
  "reason": "...",
  "issuedAt": "...",
  "expiresOn": "...", // nullable
}
```

Information on a single ban.

* `id` The ban's ID
* `revoked` True if the ban was manually revoked, false otherwise
* `author` The author of the ban (human-readable)
* `reason` The (human-readable) reason for the ban
* `issuedAt` The timestamp (ISO 8601) for when the ban was issued
* `expiresOn` The timestamp (ISO 8601) at which the ban expires, or null if the ban does not expire 

#### BanRequest

```json5
{
  "reason": "...",
  "expiresOn": "..." // nullable
}
```

Contains additional information used when banning someone.

* `reason` The reason for the ban
* `expiresOn` The expiry timestamp (ISO 8601) for the ban, or null if the ban does not expire.

### POST /admin/idrequest

**Request the identity of a user.** This will notify the "target" user (following the instance's privacy settings).

```http request
POST /api/v1/admin/idrequest
SessionId: abcdef1234 # mandatory
Content-Type: application/json # mandatory
```

The request body is an [IdRequest](#idrequest) JSON object.

Returns a [IdRequestResult](#idrequestresult) upon success. [Error code 430](#4xx-codes) is relevant here.

### GET /admin/user/{userid}

**Retrieve user information.** This is different from the `/user` endpoint.

```http request
GET /api/v1/admin/user/{targetid}
SessionId: abcdef1234 # mandatory
```

Where `{targetid}` is the Discord ID of the person. 

This endpoint returns a [RegisteredUserInfo](#registereduserinformation) about the target user.

### GET /admin/ban/{msftHash}

**Get the bans of a user using their Microsoft ID hash.**

```http request
GET /admin/ban/{msftHash}
```

Where `{msftHash}` is the Base64 (URL safe) encoded SHA256 hash of the user's Microsoft ID. You can retrieve this value for current users by calling [this endpoint](#get-adminuseruserid).

Returns a [UserBans](#userbans) object.

### GET /admin/ban/{msftHash}/{banId}

**Get a single ban of a specific user**

```http request
GET /admin/ban/{msftHash}/{banId}
```

Where `{msftHash}` is the Base64 (URL safe) encoded SHA256 hash of the user's Microsoft ID. You can retrieve this value for current users by calling [this endpoint](#get-adminuseruserid) and `{banId}` is the ID of the specific ban that needs to be retrieved.

If any of the following is true:

* No ban exists with the given ban ID
* The ban ID is incorrect (not properly formatted)
* The ban exists but does not correspond to the given `msftHash`

A 404 (if the ban does not exist or does not correspond to the `msftHash) or 400 (if the ID is not formatted properly, i.e. not an integer number) error is returned with [error code 403](#4xx-codes).

Returns a [BanInfo](#baninfo) object.

### POST /admin/ban/{msftHash}/{banId}/revoke

**Revoke a ban**

```http request
POST /admin/ban/{msftHash}/{banId}/revoke
```

Where `{msftHash}` is the Base64 (URL safe) encoded SHA256 hash of the user's Microsoft ID. You can retrieve this value for current users by calling [this endpoint](#get-adminuseruserid) and `{banId}` is the ID of the specific ban that needs to be retrieved.

Revokes the ban, making it effectively ignored. If the ban was active before being revoked, the user's roles are re-evaluated.

May return a 400 HTTP error with error code 403 if the ID does not make sense (similar to what [the GET does](#get-adminbanmsfthashbanid)).

### POST /admin/ban/{msftHash}

**Ban someone.**

```http request
POST /admin/ban/{msftHash}
SessionId: abcdef12345 # mandatory
Content-Type: application/json # mandatory
```

The request body is a [BanRequest](#banrequest) object.

Should the ban request have an invalid expiry date, the request is ignored and an HTTP 400 error with [error code 404](#4xx-codes) is returned.

Returns the newly created ban as a [BanInfo](#baninfo) object.

### GET /admin/gdprreport/{targetId}

**Generate a GDPR report about the target.**

```http request
POST /admin/gdprreport/{targetId}
SessionId: abcdef12345 # mandatory
```

Where `{targetId}` is the Discord ID of the person you want to generate a GDPR report about.

> **DOES NOT RETURN AN API RESPONSE.** This endpoint returns a Markdown document directly. `Content-Type: text/markdown`

Returns the report directly as a Markdown document. May also return an API error in case the Discord ID is invalid. Generates an ID access request (which is included in the report) in order to add the user's identity in the report.
