"use strict";

// --------
// WARNING:
// --------

// THIS CODE IS ONLY MADE AVAILABLE FOR DEMONSTRATION PURPOSES AND IS NOT SECURE!
// DO NOT USE IN PRODUCTION!

// FOR SECURITY REASONS, USING A JAVASCRIPT WEB APP HOSTED VIA THE CORDA NODE IS
// NOT THE RECOMMENDED WAY TO INTERFACE WITH CORDA NODES! HOWEVER, FOR THIS
// PRE-ALPHA RELEASE IT'S A USEFUL WAY TO EXPERIMENT WITH THE PLATFORM AS IT ALLOWS
// YOU TO QUICKLY BUILD A UI FOR DEMONSTRATION PURPOSES.

// GOING FORWARD WE RECOMMEND IMPLEMENTING A STANDALONE WEB SERVER THAT AUTHORISES
// VIA THE NODE'S RPC INTERFACE. IN THE COMING WEEKS WE'LL WRITE A TUTORIAL ON
// HOW BEST TO DO THIS.

const app = angular.module('demoAppModule', ['ui.bootstrap']);

// Fix for unhandled rejections bug.
app.config(['$qProvider', function ($qProvider) {
    $qProvider.errorOnUnhandledRejections(false);
}]);

app.controller('DemoAppController', function($http, $location, $uibModal) {
    const demoApp = this;

    // We identify the node.
    const apiBaseURL = "/api/demand/";
    let peers = [];
    let platformLeads = [];

    $http.get(apiBaseURL + "me").then((response) => demoApp.thisNode = response.data.me);

    $http.get("/api/example/peers").then((response) => peers = response.data.peers);

    $http.get("/api/demand/platformLeads").then((response) => platformLeads = response.data.plPeers);
    demoApp.plResponse = platformLeads;

    demoApp.openCreateDemandModal = () => {
        const modalInstance = $uibModal.open({
            templateUrl: 'createDemandModal.html',
            controller: 'ModalCreateDemandCtrl',
            controllerAs: 'createModalInstance',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
                peers: () => platformLeads
            }
        });

        modalInstance.result.then(() => {}, () => {});
    };

    demoApp.openUpdateDemandModal = (demand) => {
        const modalInstance = $uibModal.open({
            templateUrl: 'updateDemandModal.html',
            controller: 'ModalUpdateDemandCtrl',
            controllerAs: 'modalInstance',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
                peers: () => peers,
                id: () => demand.linearId.id,
                sponsor: () => demand.sponsor,
                platformLead: () => demand.platformLead,
                description: () => demand.description,
            }
        });

        modalInstance.result.then(() => {}, () => {});
    };

    demoApp.getDemands = () => $http.get(apiBaseURL)
        .then((response) => demoApp.demands = Object.keys(response.data)
            .map((key) => response.data[key].state.data)
            .reverse());

    demoApp.getDemands();

    //refresh every 5s
    setInterval(function(){
        demoApp.getDemands();
    }, 5000);
});

app.controller('ModalUpdateDemandCtrl', function ($http, $location, $uibModalInstance, $uibModal, demoApp, apiBaseURL, peers, id, sponsor, platformLead, description) {
    const modalInstance = this;

    modalInstance.peers = peers;
    modalInstance.form = {};
    modalInstance.formError = false;
    modalInstance.id = id;
    modalInstance.sponsor = sponsor;
    modalInstance.platformLead = platformLead;
    modalInstance.description = description;

    // Validate and create Demand.
    modalInstance.update = () => {
        if (invalidFormInput()) {
            modalInstance.formError = true;
        } else {
            modalInstance.formError = false;

            $uibModalInstance.close();

            //TODO add update call
        }
    };

    modalInstance.displayMessage = (message) => {
        const modalInstanceTwo = $uibModal.open({
            templateUrl: 'messageContent.html',
            controller: 'messageCtrl',
            controllerAs: 'modalInstanceTwo',
            resolve: { message: () => message }
        });

        // No behaviour on close / dismiss.
        modalInstanceTwo.result.then(() => {}, () => {});
    };

    // Close create Demand modal dialogue.
    modalInstance.cancel = () => $uibModalInstance.dismiss();

    // Validate Demand Creation.
    function invalidFormInput() {
        return isNaN(modalInstance.form.amount) || (modalInstance.form.startDate === undefined) || (modalInstance.form.endDate === undefined);
    }
});

app.controller('ModalCreateDemandCtrl', function ($http, $location, $uibModalInstance, $uibModal, demoApp, apiBaseURL, peers) {
    const createModalInstance = this;

    createModalInstance.peers = peers;
    createModalInstance.form = {};
    createModalInstance.formError = false;

    // Validate and create Demand.
    createModalInstance.create = () => {
        if (invalidFormInput()) {
            createModalInstance.formError = true;
        } else {
            createModalInstance.formError = false;

            $uibModalInstance.close();

            const createDemandEndpoint = `${apiBaseURL}create-demand?partyName=${createModalInstance.form.counterparty}&description=${createModalInstance.form.description}`;

            // Create PO and handle success / fail responses.
            $http.post(createDemandEndpoint).then(
                (result) => {
                    createModalInstance.displayMessage(result);
                    demoApp.getDemands();
                },
                (result) => {
                    createModalInstance.displayMessage(result);
                }
            );
        }
    };

    createModalInstance.displayMessage = (message) => {
        const modalInstanceTwo = $uibModal.open({
            templateUrl: 'messageContent.html',
            controller: 'messageCtrl',
            controllerAs: 'modalInstanceTwo',
            resolve: { message: () => message }
        });

        // No behaviour on close / dismiss.
        modalInstanceTwo.result.then(() => {}, () => {});
    };

    // Close create Demand modal dialogue.
    createModalInstance.cancel = () => $uibModalInstance.dismiss();

    // Validate Demand Creation.
    function invalidFormInput() {
        return (createModalInstance.form.description === undefined) || (createModalInstance.form.counterparty === undefined);
    }
});

// Controller for success/fail modal dialogue.
app.controller('messageCtrl', function ($uibModalInstance, message) {
    const modalInstanceTwo = this;
    modalInstanceTwo.message = message.data;
});