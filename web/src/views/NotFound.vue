<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div id="not-found">
        <h1 id="title">404</h1>
        <span id="description" v-html="$t('notFound.description')" />
    </div>
</template>

<script>
    export default {
        name: 'link-not-found'
    }
</script>

<style lang="scss" scoped>
    @import '../styles/mixins';

    #not-found {
        display: flex;
        flex-direction: column;
        justify-content: space-evenly;
        align-items: center;

        padding: 50px;
    }

    #title {
        @include lato(bold);
        font-size: 112px;

        margin: 0;
    }

    #description {
        font-size: 25px;
        text-align: center;
    }
</style>