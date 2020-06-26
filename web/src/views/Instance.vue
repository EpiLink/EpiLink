<!--

    This Source Code Form is subject to the terms of the Mozilla Public
    License, v. 2.0. If a copy of the MPL was not distributed with this
    file, You can obtain one at http://mozilla.org/MPL/2.0/.

    This Source Code Form is "Incompatible With Secondary Licenses", as
    defined by the Mozilla Public License, v. 2.0.

-->
<template>
    <div id="instance">
        <div id="banner">
            <img id="logo" v-if="logo" :src="logo"/>
            <h1 class="title">{{ title }}</h1>
        </div>
        <div id="legal-links">
            <router-link class="link" to="/tos">{{ $t('layout.navigation.tos')}}</router-link>
            <router-link class="link" to="/privacy">{{ $t('layout.navigation.privacy')}}</router-link>
        </div>
        <div id="contact">
            <h2 id="contact-info">{{ $t('instance.contactTitle') }}</h2>
            <p id="contact-description">{{ $t('instance.contactDesc') }}</p>
            <ul id="contacts">
                <li v-for="p of people">
                    {{ p.name }} (<a :href="'mailto:' + p.email">{{ p.email }}</a>)
                </li>
            </ul>
        </div>
        <div id="powered">
            <router-link id="about" to="/about">
                <span class="title">{{ $t('instance.poweredBy') }}</span>
                <img id="logo" src="../../assets/logo.svg" />
                <span class="title" id="title-epilink">EpiLink</span>
            </router-link>
        </div>
    </div>
</template>

<script>
    import { mapState } from "vuex";

    export default {
        name: "link-instance",
        computed: mapState({
            title: s => s.meta.title,
            logo: s => s.meta.logo,
            people: s => [{ name: 'Adrien Navratil', email: 'zbeub@zzz.fr' }]
        })
    }
</script>

<style lang="scss" scoped>
    @import '../styles/fonts';

    #instance {
        padding: 32px;

        display: flex;
        flex-direction: column;
        justify-content: space-between;
    }

    #banner {
        width: 100%;

        display: flex;
        align-self: center;
        justify-content: center;
        align-items: center;

        #logo {
            width: 85px;
            margin-right: 20px;

            border-radius: 5px;
        }

        .title {
            font-size: 40px;

            margin: 0;

            text-overflow: ellipsis;
            overflow: hidden;
            white-space: nowrap;
        }
    }

    #powered {
        align-self: center;

        #about {
            display: contents;
        }

        #logo {
            width: 27px;
            height: 27px;

            margin: 0 6px 8px 12px;
            vertical-align: middle;
            border-radius: 3px;
        }

        .title {
            font-size: 23px;
            @include lato(regular);
        }

        #title-epilink {
            @include lato(bold);
        }
    }

    #legal-links {
        width: 100%;

        display: flex;
        justify-content: center;
        align-items: center;

        .link {
            flex: 1 0;
            text-align: center;
            font-weight: bold;
        }
    }

    #contact {
        display: flex;
        flex-direction: column;
        justify-content: center;

        #contact-info {
            margin-top: 0;
            margin-bottom: 0;
        }

        #contact-description {
            margin-bottom: 0;
        }
    }

    @media screen and (max-width: 425px) {
        #banner .title {
            font-size: 32px;
        }
    }

    @media screen and (max-width: 375px) {
        #instance {
            padding: 32px 25px;
        }

        #contact-info {
            font-size: 20px;
        }

        #powered #about .title {
            font-size: 21px;
        }
    }

    @media screen and (max-width: 375px) {
        #contact-description, #contacts {
            font-size: 14px;
        }
    }
</style>