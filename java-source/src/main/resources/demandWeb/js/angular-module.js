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
    const apiProjBaseUrl = "/api/project/";

    let peers = [];
    let platformLeads = [];

    $http.get(apiBaseURL + "me").then(function(response){
        demoApp.thisNode = response.data.me;

        let organisation = demoApp.thisNode.split(",")[2].split("=")[1];
        demoApp.isSponsor = organisation === "Sponsor";
        demoApp.isPlatformLead = organisation.substring(0,2) === "PL";
    });

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
            controllerAs: 'updateModalInstance',
            resolve: {
                demoApp: () => demoApp,
                apiBaseURL: () => apiBaseURL,
                peers: () => peers,
                id: () => demand.linearId.id,
                lender: () => demand.lender,
                borrower: () => demand.borrower,
                description: () => demand.description,
            }
        });

        modalInstance.result.then(() => {}, () => {});
    };

    demoApp.getDemands = () => $http.get(apiBaseURL)
        .then((response) => demoApp.demands = Object.keys(response.data)
            .map((key) => response.data[key].state.data)
            .reverse());

    demoApp.getProjects = () => $http.get("/api/project/")
        .then((response) => demoApp.projects = Object.keys(response.data)
            .map((key) => response.data[key].state.data)
            .reverse());


    demoApp.getDemands();
    demoApp.getProjects();

    //refresh every 5s
    setInterval(function(){
        demoApp.getDemands();
        demoApp.getProjects();
    }, 5000);
});

app.controller('ModalUpdateDemandCtrl', function ($http, $location, $uibModalInstance, $uibModal, demoApp, apiBaseURL, peers, id, lender, borrower, description) {
    const updateModalInstance = this;

    updateModalInstance.peers = peers;
    updateModalInstance.form = {};
    updateModalInstance.formError = false;
    updateModalInstance.id = id;
    updateModalInstance.lender = lender;
    updateModalInstance.borrower = borrower;
    updateModalInstance.description = description;


    // Validate and create Demand.
    updateModalInstance.update = (id) => {
        if (invalidFormInput()) {
            updateModalInstance.formError = true;
        } else {
            updateModalInstance.formError = false;

            $uibModalInstance.close();

            let startDate = formatDate(updateModalInstance.form.startDate);
            let endDate = formatDate(updateModalInstance.form.endDate);
            const updateDemandEndpoint = `${apiBaseURL}update-demand?amount=${updateModalInstance.form.amount}&startDate=${startDate}&endDate=${endDate}&id=${id}`;

            $http.post(updateDemandEndpoint).then(
                (result) => {
                    updateModalInstance.displayMessage(result);
                    demoApp.getDemands();
                    demoApp.getProjects();
                },
                (result) => {
                    updateModalInstance.displayMessage(result);
                }
            );
        }
    };

    updateModalInstance.displayMessage = (message) => {
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
    updateModalInstance.cancel = () => $uibModalInstance.dismiss();

    // Validate Demand Creation.
    function invalidFormInput() {
        return isNaN(updateModalInstance.form.amount) || (updateModalInstance.form.startDate === undefined) || (updateModalInstance.form.endDate === undefined);
    }

    //format date
    function formatDate(date){
        let month = '' + (date.getMonth() + 1),
            day = '' + date.getDate(),
            year = date.getFullYear();

        if (month.length < 2) month = '0' + month;
        if (day.length < 2) day = '0' + day;

        return [day, month, year].join('/');
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
                    demoApp.getProjects();
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